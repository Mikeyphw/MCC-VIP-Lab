package dev.mikey.mccviplab;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Read-only observation of Android SharedPreferences. Flutter AOT memory is not visible here. */
public final class LocalStateProbe {
    private static final String TAG = "MCCLOCALQA";
    private static final int MAX_LOGS = 80;
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final Map<String, Long> LAST = new HashMap<>();
    private LocalStateProbe() {}

    public static String category(String key) {
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("diamond") || k.contains("coin") || k.contains("gem")) return "diamond";
        if (k.contains("premium") || k.contains("subscription") || k.contains("vip") || k.contains("entitlement")) return "premium";
        if (k.contains("validuntil") || k.contains("expir") || k.contains("renewal")) return "expiry";
        if (k.contains("adblock") || k.contains("ad_block")) return "ad_block";
        // A generic balance/reward may contain personal or unrelated information; do not log.
        return null;
    }

    private static synchronized boolean allow(String signature, long now) {
        Long previous = LAST.put(signature, now);
        if (LAST.size() > 100) LAST.clear();
        return previous == null || now - previous >= 1000L;
    }

    private static void observe(String operation, Object[] args, Object value) {
        if (args == null || args.length < 1 || !(args[0] instanceof String)) return;
        String category = category((String) args[0]);
        if (category == null) return;
        String safeValue;
        if (value instanceof Integer || value instanceof Long || value instanceof Boolean)
            safeValue = String.valueOf(value);
        else safeValue = "redacted";
        String signature = operation + ":" + category + ":" + safeValue;
        if (!allow(signature, System.currentTimeMillis())) return;
        int n = LOGS.incrementAndGet();
        if (n > MAX_LOGS) return;
        XposedBridge.log(TAG + ": " + operation + " category=" + category + " value=" + safeValue);
        if (n == MAX_LOGS) XposedBridge.log(TAG + ": LOG_LIMIT_REACHED");
    }

    public static void install() {
        int success = 0;
        try {
            Class<?> prefs = Class.forName("android.app.SharedPreferencesImpl", false, null);
            for (String method : new String[] {"getInt", "getLong", "getBoolean", "getString"}) {
                try {
                    XposedBridge.hookAllMethods(prefs, method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            observe("PREF_READ", param.args, param.getResult());
                        }
                    });
                    success++;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { XposedBridge.log(TAG + ": READ_UNAVAILABLE " + t.getClass().getSimpleName()); }
        try {
            Class<?> editor = Class.forName("android.app.SharedPreferencesImpl$EditorImpl", false, null);
            for (String method : new String[] {"putInt", "putLong", "putBoolean", "putString", "remove"}) {
                try {
                    XposedBridge.hookAllMethods(editor, method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Object value = param.args != null && param.args.length > 1 ? param.args[1] : null;
                            observe("PREF_WRITE_ATTEMPT", param.args, value);
                        }
                    });
                    success++;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { XposedBridge.log(TAG + ": WRITE_UNAVAILABLE " + t.getClass().getSimpleName()); }
        XposedBridge.log(TAG + ": SHARED_PREF_PROBE_INSTALLED_READ_ONLY methods=" + success);
    }
}
