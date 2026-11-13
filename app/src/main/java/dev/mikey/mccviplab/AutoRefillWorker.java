package dev.mikey.mccviplab;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Executes the P09 force-stop/write/relaunch sequence from the MODULE process. */
final class AutoRefillWorker {
    private static final String TAG = "MCCREFILL";
    private static final String PREFS = "mcc_refill_control";
    private static final long TIMEOUT_SECONDS = 45;
    private AutoRefillWorker() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean autoEnabled(Context context) {
        return prefs(context).getBoolean("auto_enabled", true);
    }
    static void setAutoEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("auto_enabled", enabled).apply();
    }
    static String lastResult(Context context) {
        return prefs(context).getString("last_status", "No refill requested yet");
    }
    private static void remember(Context context, String status) {
        prefs(context).edit().putString("last_status", status).apply();
        Log.i(TAG, status);
    }

    /** Requests are serialized; one worker process survives force-stopping the target. */
    static synchronized String execute(Context context, boolean auto) {
        if (auto && !autoEnabled(context)) return "AUTO_DISABLED";
        if (auto) {
            long now = System.currentTimeMillis();
            long last = prefs(context).getLong("last_auto_attempt_ms", 0);
            if (now >= last && now - last < 12000L) return "AUTO_COOLDOWN";
            // commit, not apply: the retry guard must survive process restarts.
            prefs(context).edit().putLong("last_auto_attempt_ms", now).commit();
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("su", "-c", RootRefillCommand.script(auto));
            builder.redirectErrorStream(true);
            process = builder.start();
            // No shell output containing account-specific filenames is saved or displayed.
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                remember(context, "ROOT_TIMEOUT: check KernelSU permission for MCC VIP Lab");
                return "ROOT_TIMEOUT";
            }
            String line;
            boolean passed = false;
            boolean skip = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while ((line = reader.readLine()) != null) {
                    if (line.equals("MCC_REFILL_P09_SEQUENCE_PASS")) passed = true;
                    if (line.equals("MCC_REFILL_AUTO_SKIP_NONZERO")) skip = true;
                }
            }
            if (process.exitValue() == 0 && passed) {
                remember(context, auto ? "AUTO_0_TO_9_RESTART_PASS" : "MANUAL_SET_9_RESTART_PASS");
                return "APPLIED";
            }
            if (skip && process.exitValue() == 63) {
                remember(context, "AUTO_SKIP_NONZERO_AFTER_STOP");
                return "SKIPPED_NONZERO";
            }
            String status = "ROOT_REFUSED_exit_" + process.exitValue();
            remember(context, status);
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            remember(context, "INTERRUPTED");
            return "INTERRUPTED";
        } catch (Throwable error) {
            String status = "ROOT_UNAVAILABLE_" + error.getClass().getSimpleName();
            remember(context, status);
            return status;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
