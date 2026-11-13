package dev.mikey.mccviplab;

import android.content.Context;
import android.provider.Settings;

import java.util.concurrent.TimeUnit;

/** Two fixed, user-controlled Settings.Global flags; writes run only in the module UID. */
final class CosmeticControls {
    static final String BANNER = "mcc_lab_banner_mode";
    static final String UPSELL = "mcc_lab_upsell_mode";
    private CosmeticControls() {}

    static boolean enabled(Context ctx, String key) {
        if (!BANNER.equals(key) && !UPSELL.equals(key)) return false;
        try {
            // Personal P11 feature branch defaults both to hide; 'show' disables.
            return !"show".equals(Settings.Global.getString(ctx.getContentResolver(), key));
        } catch (Throwable ignored) { return false; }
    }

    static String update(String key, boolean hide) {
        if (!BANNER.equals(key) && !UPSELL.equals(key)) return "REFUSED_UNKNOWN_MODE";
        String value = hide ? "hide" : "show";
        String cmd = "[ \"$(id -u)\" = 0 ] && settings put global " + key + " " + value +
                " && [ \"$(settings get global " + key + ")\" = " + value + " ]";
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            if (!p.waitFor(12, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "MODE_WRITE_TIMEOUT";
            }
            return p.exitValue() == 0 ? "MODE_SAVED_REOPEN_TARGET_TO_APPLY" :
                    "MODE_WRITE_FAILED_EXIT_" + p.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "MODE_WRITE_INTERRUPTED";
        } catch (Throwable e) {
            return "MODE_WRITE_UNAVAILABLE_" + e.getClass().getSimpleName();
        } finally { if (p != null) p.destroy(); }
    }
}
