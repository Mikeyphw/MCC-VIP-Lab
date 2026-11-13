package dev.mikey.mccviplab;

import android.content.Context;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/** Observe persistent zero, then ask the foreground target Activity to open the MODULE worker. */
public final class AutoRefillWatcher {
    private static final String TAG = "MCCREFILL";
    private static final long POLL_MS = 1200L;
    private static final long QUIET_PERIOD_MS = 750L;
    private static final long REQUEST_GAP_MS = 15000L;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static Handler handler;
    private static FileObserver observer;
    private static File directory;
    private static long zeroFirstSeen;
    private static long lastRequest;
    private static int logged;
    private AutoRefillWatcher() {}

    private static void note(String value) {
        if (logged++ < 40) XposedBridge.log(TAG + ": " + value);
    }
    private static void poll() {
        File file = AutoRefillPolicy.uniqueBalanceFile(directory);
        if (file == null || file.length() != 1) { zeroFirstSeen = 0; return; }
        try {
            if (!AutoRefillPolicy.exactZero(Files.readAllBytes(file.toPath()))) {
                zeroFirstSeen = 0;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (zeroFirstSeen == 0) { zeroFirstSeen = now; return; }
            if (now - zeroFirstSeen < QUIET_PERIOD_MS || now - lastRequest < REQUEST_GAP_MS) return;
            // No provider call from target UID: use foreground module Activity identity handoff.
            if (InAppRefillButton.requestAutomatic()) {
                lastRequest = now;
                note("AUTO_REQUEST_CONTROL_ACTIVITY_DIRECT=true");
            } else {
                note("AUTO_PENDING_FOREGROUND_ACTIVITY");
            }
        } catch (Throwable error) {
            lastRequest = SystemClock.elapsedRealtime();
            note("AUTO_OBSERVE_FAILED=" + error.getClass().getSimpleName());
        }
    }
    private static void startObserver() {
        if (observer != null || !directory.isDirectory()) return;
        try {
            observer = new FileObserver(directory.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
                @Override public void onEvent(int event, String relativePath) {
                    if (!AutoRefillPolicy.allowedName(relativePath)) return;
                    Handler h = handler;
                    if (h != null) h.post(AutoRefillWatcher::poll);
                }
            };
            observer.startWatching();
            note("WATCHING_ZERO via_explicit_module_activity=true");
        } catch (Throwable error) {
            observer = null;
            note("OBSERVER_UNAVAILABLE fallback=poll");
        }
    }
    public static void install(Context appContext) {
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            directory = new File(appContext.getApplicationInfo().dataDir, "databases");
            HandlerThread worker = new HandlerThread("mcc-zero-personal");
            worker.start();
            handler = new Handler(worker.getLooper());
            handler.post(new Runnable() {
                @Override public void run() {
                    startObserver();
                    poll();
                    handler.postDelayed(this, POLL_MS);
                }
            });
            note("INSTALLED version_agnostic=true stop_write_restart=true");
        } catch (Throwable error) {
            STARTED.set(false);
            note("INSTALL_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
