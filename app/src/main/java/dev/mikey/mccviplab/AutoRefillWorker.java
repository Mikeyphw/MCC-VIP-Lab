package dev.mikey.mccviplab;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Root writes occur in the independent MCC module app process, never in Flutter. */
final class AutoRefillWorker {
    private static final String TAG = "MCCREFILL";
    private static final String PREFS = "mcc_refill_control";
    private static final long TIMEOUT_SECONDS = 20;
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
    /** Launch does NOT affect whether the root write is considered successful. */
    private static String dispatchLaunch(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getPackageManager().getLaunchIntentSenderForPackage(RootRefillCommand.TARGET)
                    .sendIntent(context, 0, null, null, null);
            } else {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(RootRefillCommand.TARGET);
                if (intent == null) return "LAUNCH_UNAVAILABLE";
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return "LAUNCH_DISPATCHED_NOT_VERIFIED";
        } catch (Throwable error) {
            return "LAUNCH_FAILED_" + error.getClass().getSimpleName();
        }
    }

    /** Only called from MCC module Activity; serialize requests and bound shell output. */
    static synchronized String execute(Context context, boolean auto) {
        if (auto && !autoEnabled(context)) return "AUTO_DISABLED";
        if (auto) {
            long now = System.currentTimeMillis();
            long last = prefs(context).getLong("last_auto_attempt_ms", 0);
            if (now >= last && now - last < 12000L) return "AUTO_COOLDOWN";
            prefs(context).edit().putLong("last_auto_attempt_ms", now).commit();
        }
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", RootRefillCommand.script(auto))
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                remember(context, "ROOT_COMMAND_TIMEOUT");
                return "ROOT_COMMAND_TIMEOUT";
            }
            boolean verified = false;
            boolean skipped = false;
            int lines = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && ++lines < 100) {
                    if (line.equals("MCC_REFILL_WRITE_VERIFIED")) verified = true;
                    if (line.equals("MCC_REFILL_AUTO_SKIP_NONZERO")) skipped = true;
                }
            }
            int exitCode = process.exitValue();
            if (verified && exitCode == 0) {
                String status = "WRITE_9_VERIFIED_" + dispatchLaunch(context);
                remember(context, status);
                return status;
            }
            if (auto && skipped && exitCode == 63) {
                String status = "AUTO_SKIP_NONZERO_" + dispatchLaunch(context);
                remember(context, status);
                return status;
            }
            // Even on a failure the command may have force-stopped the target.
            String launch = dispatchLaunch(context);
            String status = exitCode == 70 ? "ROOT_UID_NOT_ZERO_" + launch :
                    "REFILL_SCRIPT_EXIT_" + exitCode + "_" + launch;
            remember(context, status);
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            remember(context, "REFILL_INTERRUPTED");
            return "REFILL_INTERRUPTED";
        } catch (Throwable error) {
            String status = "ROOT_COMMAND_UNAVAILABLE_" + error.getClass().getSimpleName();
            remember(context, status);
            return status;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
