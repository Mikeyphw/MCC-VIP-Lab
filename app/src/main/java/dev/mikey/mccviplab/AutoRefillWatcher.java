package dev.mikey.mccviplab;

import android.content.Context;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/**
 * Experimental local-only storage refill: convert one-byte ASCII '0' to '9'.
 * It cannot update MobX/Dart memory directly, and it does not establish server credit.
 * Runs only inside the scoped target process while that process is alive.
 */
public final class AutoRefillWatcher {
    private static final String TAG = "MCCREFILL";
    private static final String SETTING = "mcc_lab_refill_9_enabled";
    private static final long FALLBACK_POLL_MS = 1200L;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean(false);
    private static Handler threadHandler;
    private static FileObserver directoryObserver;
    private static File databaseDirectory;
    private static Context context;
    private static int successfulRefills;
    private static int failures;

    private AutoRefillWatcher() {}

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static boolean enabled() {
        try {
            return !"0".equals(Settings.Global.getString(context.getContentResolver(), SETTING));
        } catch (Throwable error) {
            // Setting is optional; the dedicated feature branch defaults to enabled.
            return true;
        }
    }

    private static boolean stillZero(File target, long size, long lastModified) throws Exception {
        if (!target.isFile() || Files.isSymbolicLink(target.toPath()) ||
                target.length() != size || target.lastModified() != lastModified) return false;
        return AutoRefillPolicy.exactZero(Files.readAllBytes(target.toPath()));
    }

    /** Only called on the background HandlerThread. Never edit non-zero balances. */
    private static void check(final String reason) {
        if (!enabled()) return;
        File target = AutoRefillPolicy.uniqueBalanceFile(databaseDirectory);
        if (target == null || target.length() != 1L) return;
        File temporary = null;
        try {
            long lastModified = target.lastModified();
            byte[] current = Files.readAllBytes(target.toPath());
            byte[] updated = AutoRefillPolicy.nineOnlyWhenZero(current);
            if (updated == null) return;
            // Same-directory atomic replacement avoids exposing a partially-written file.
            temporary = File.createTempFile(".mcc-refill-", ".tmp", databaseDirectory);
            try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
                stream.write(updated);
                stream.getFD().sync();
            }
            if (!stillZero(target, current.length, lastModified)) return;
            // An atomic rename is required; never fall back to truncating the live file.
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            if (successfulRefills++ < 30) log("ZERO_TO_NINE_PERSISTED source=" + reason +
                    " local_file_only=true ui_refresh_unverified=true");
        } catch (Throwable failure) {
            if (failures++ < 10) log("FILE_UPDATE_REFUSED reason=" + failure.getClass().getSimpleName());
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private static void startObserver() {
        if (directoryObserver != null || !databaseDirectory.isDirectory()) return;
        try {
            directoryObserver = new FileObserver(databaseDirectory.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
                @Override public void onEvent(int event, String relativePath) {
                    if (!AutoRefillPolicy.allowedName(relativePath)) return;
                    Handler handler = threadHandler;
                    if (handler != null) handler.post(() -> check("file_observer"));
                }
            };
            directoryObserver.startWatching();
            log("WATCHER_STARTED event_and_periodic_fallback=true");
        } catch (Throwable failure) {
            directoryObserver = null;
            log("FILE_OBSERVER_UNAVAILABLE fallback=poll reason=" + failure.getClass().getSimpleName());
        }
    }

    public static void install(Context applicationContext) {
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            context = applicationContext.getApplicationContext();
            databaseDirectory = new File(context.getApplicationInfo().dataDir, "databases");
            HandlerThread worker = new HandlerThread("mcc-refill-nine");
            worker.start();
            threadHandler = new Handler(worker.getLooper());
            final Runnable tick = new Runnable() {
                @Override public void run() {
                    startObserver();
                    check("periodic");
                    Handler handler = threadHandler;
                    if (handler != null) handler.postDelayed(this, FALLBACK_POLL_MS);
                }
            };
            threadHandler.post(tick);
            if (ANNOUNCED.compareAndSet(false, true)) {
                log("INSTALLED target_process_only=true zero_to_nine=true default_enabled=true " +
                        "flutter_memory_not_hooked=true backend_not_verified=true");
            }
        } catch (Throwable error) {
            STARTED.set(false);
            log("INSTALL_FAILED reason=" + error.getClass().getSimpleName());
        }
    }
}
