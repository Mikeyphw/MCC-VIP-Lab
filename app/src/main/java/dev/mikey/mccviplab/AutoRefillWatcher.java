package dev.mikey.mccviplab;

import android.content.Context;
import android.net.Uri;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/**
 * Detects exactly one ASCII '0' in target file, then requests the separate
 * module process to repeat the proven P09 stop/write/restart sequence.
 * Never edits the file in the Flutter process (would leave MobX stale).
 */
public final class AutoRefillWatcher {
    private static final String TAG = "MCCREFILL";
    private static final String AUTHORITY = "content://" + RootRefillCommand.PROVIDER_AUTHORITY;
    private static final long POLL_MS = 1200L;
    private static final long QUIET_PERIOD_MS = 750L;
    private static final long REQUEST_GAP_MS = 6000L;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static Handler handler;
    private static FileObserver observer;
    private static File directory;
    private static Context context;
    private static long zeroFirstSeen;
    private static long lastRequest;
    private static int logged;
    private AutoRefillWatcher() {}

    private static void note(String value) {
        if (logged++ < 40) XposedBridge.log(TAG + ": " + value);
    }

    private static void poll() {
        File balance = AutoRefillPolicy.uniqueBalanceFile(directory);
        if (balance == null || balance.length() != 1) {
            zeroFirstSeen = 0;
            return;
        }
        try {
            if (!AutoRefillPolicy.exactZero(Files.readAllBytes(balance.toPath()))) {
                zeroFirstSeen = 0;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (zeroFirstSeen == 0) { zeroFirstSeen = now; return; }
            if (now - zeroFirstSeen < QUIET_PERIOD_MS || now - lastRequest < REQUEST_GAP_MS) return;
            lastRequest = now;
            // This call runs in the target process, but root commands run in
            // the installed module's separate Android process.
            android.os.Bundle response = context.getContentResolver().call(
                    Uri.parse(AUTHORITY), "AUTO_ZERO", null, null);
            String status = response == null ? "NO_RESPONSE" : response.getString("result", "NO_RESULT");
            note("AUTO_REQUEST_RESULT=" + status + " P09_RESTART_SEQUENCE=true");
        } catch (Throwable error) {
            lastRequest = SystemClock.elapsedRealtime();
            note("AUTO_PROVIDER_UNAVAILABLE=" + error.getClass().getSimpleName());
        }
    }

    private static void startObserver() {
        if (observer != null || !directory.isDirectory()) return;
        try {
            observer = new FileObserver(directory.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
                @Override public void onEvent(int event, String path) {
                    if (!AutoRefillPolicy.allowedName(path)) return;
                    Handler h = handler;
                    if (h != null) h.post(AutoRefillWatcher::poll);
                }
            };
            observer.startWatching();
            note("WATCHING_0_WITH_P09_RESTART provider=module_process");
        } catch (Throwable error) {
            observer = null;
            note("OBSERVER_UNAVAILABLE periodic_poll_remains=true");
        }
    }

    public static void install(Context appContext) {
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            context = appContext.getApplicationContext();
            directory = new File(context.getApplicationInfo().dataDir, "databases");
            HandlerThread thread = new HandlerThread("mcc-zero-restart-observer");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(new Runnable() {
                @Override public void run() {
                    startObserver();
                    poll();
                    handler.postDelayed(this, POLL_MS);
                }
            });
            note("INSTALLED p09_force_stop_write_restart=true default_auto_enabled_in_module=true");
        } catch (Throwable error) {
            STARTED.set(false);
            note("INSTALL_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
