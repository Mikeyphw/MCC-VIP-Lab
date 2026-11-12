package dev.mikey.mccviplab;

import android.content.Context;
import android.provider.Settings;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * In-process numeric SharedPreferences tests only. No ad-event fabrication,
 * no SQL or backend writes. Flutter/Dart-only balances are outside this hook.
 */
public final class DiamondProbe {
    private static final Map<String, Long> FROZEN = new HashMap<>();
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final int MAX_EVENTS = 70;
    private DiamondProbe() {}

    private static void log(String text) {
        if (EVENTS.incrementAndGet() <= MAX_EVENTS) XposedBridge.log("MCCDIAMOND: " + text);
    }
    private static String keyId(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(String.format("%02x", hash[i] & 255));
            return sb.toString();
        } catch (Throwable ignored) { return "hidden"; }
    }
    private static String mode(Context ctx) {
        try {
            String val = Settings.Global.getString(ctx.getContentResolver(), "mcc_lab_diamond_mode");
            return "high".equals(val) || "freeze".equals(val) ? val : "observe";
        } catch (Throwable ignored) { return "observe"; }
    }
    private static long highValue(Context ctx) {
        try {
            long n = Settings.Global.getLong(ctx.getContentResolver(), "mcc_lab_diamond_high", 999999L);
            return Math.max(0, Math.min(1000000000L, n));
        } catch (Throwable ignored) { return 999999L; }
    }
    private static Object evaluate(Context ctx, String operation, String key, Object value) {
        if (!DiamondPolicy.candidate(key)) return value;
        if (!(value instanceof Number)) {
            log(operation + " candidate_id=" + keyId(key) + " numeric=false");
            return value;
        }
        String currentMode = mode(ctx);
        boolean exact = DiamondPolicy.exactKey(key);
        if (!exact) {
            log(operation + " candidate_id=" + keyId(key) + " exact=false");
            return value;
        }
        Number original = (Number) value;
        Number changed = DiamondPolicy.transform(key, original, currentMode, highValue(ctx), FROZEN);
        log(operation + " candidate_id=" + keyId(key) + " exact=true" +
            " observed=" + original.longValue() + " mode=" + currentMode +
            " returned=" + changed.longValue() + " changed=" +
            (original.longValue() != changed.longValue()));
        return changed;
    }

    public static void install(final Context context) {
        try {
            Class<?> prefs = Class.forName("android.app.SharedPreferencesImpl", false, null);
            for (String method : new String[]{"getInt", "getLong"}) {
                XposedBridge.hookAllMethods(prefs, method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        Object original = p.getResult();
                        Object changed = evaluate(context, "READ", (String)p.args[0], original);
                        if (original != changed && !original.equals(changed)) p.setResult(changed);
                    }
                });
            }
            // Observe potential credit attempts. No editor arguments are modified.
            Class<?> editor = Class.forName("android.app.SharedPreferencesImpl$EditorImpl", false, null);
            for (String method : new String[]{"putInt", "putLong"}) {
                XposedBridge.hookAllMethods(editor, method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args == null || p.args.length < 2 ||
                                !(p.args[0] instanceof String) || !(p.args[1] instanceof Number)) return;
                        String key = (String) p.args[0];
                        if (!DiamondPolicy.candidate(key)) return;
                        Object value = p.args[1];
                        log("WRITE_ATTEMPT candidate_id=" + keyId(key) +
                                " exact=" + DiamondPolicy.exactKey(key) +
                                " requested=" + ((Number)value).longValue() +
                                " mode=observe_only");
                    }
                });
            }
            XposedBridge.hookAllMethods(prefs, "getAll", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(p.getResult() instanceof Map)) return;
                    Map<?, ?> original = (Map<?, ?>)p.getResult();
                    Map<Object,Object> copied = new HashMap<>();
                    boolean changed = false;
                    for (Map.Entry<?, ?> e : original.entrySet()) {
                        Object val = e.getValue();
                        if (e.getKey() instanceof String) {
                            Object next = evaluate(context,"READ_ALL",(String)e.getKey(),val);
                            if (next != val && !next.equals(val)) changed = true;
                            val = next;
                        }
                        copied.put(e.getKey(), val);
                    }
                    if (changed) p.setResult(copied);
                }
            });
            XposedBridge.log("MCCDIAMOND: PREF_NUMERIC_PROBE_INSTALLED mode=" + mode(context));
        } catch (Throwable error) {
            XposedBridge.log("MCCDIAMOND: PREF_NUMERIC_PROBE_UNAVAILABLE " + error.getClass().getSimpleName());
        }
    }
}
