package dev.mikey.mccviplab;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Read-only subscription platform-call observations; never logs call arguments. */
public final class PurchaseCallProbe {
    private static final String TAG = "MCCVIPFLOW";
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private PurchaseCallProbe() {}

    public static void install(ClassLoader loader) {
        try {
            Class<?> plugin = XposedHelpers.findClass("com.revenuecat.purchases_flutter.PurchasesFlutterPlugin", loader);
            XposedBridge.hookAllMethods(plugin, "onMethodCall", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1 || param.args[0] == null) return;
                    String method;
                    try {
                        Field field = param.args[0].getClass().getField("method");
                        Object value = field.get(param.args[0]);
                        if (!(value instanceof String)) return;
                        method = (String) value;
                    } catch (Throwable ignored) { return; }
                    String m = method.toLowerCase(Locale.ROOT);
                    if (!(m.contains("customerinfo") || m.contains("offerings") ||
                            m.contains("entitlement") || m.contains("restore") ||
                            m.equals("login") || m.equals("logout"))) return;
                    if (EVENTS.incrementAndGet() > 40) return;
                    XposedBridge.log(TAG + ": PURCHASE_CALL " + safeName(method));
                }
            });
            XposedBridge.log(TAG + ": PURCHASE_CALL_PROBE_INSTALLED_READ_ONLY");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PURCHASE_CALL_PROBE_UNAVAILABLE " + t.getClass().getSimpleName());
        }
    }

    private static String safeName(String value) {
        // Method names only; never include arguments, account IDs or tokens.
        return value.matches("[A-Za-z_]{1,56}") ? value : "other";
    }
}
