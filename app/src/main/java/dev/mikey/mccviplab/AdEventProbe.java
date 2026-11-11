package dev.mikey.mccviplab;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Read-only app-process bridge instrumentation, not rewarded-ad simulation.
 * Never emits ad success events or exposes SDK identifiers, account IDs or payloads.
 */
public final class AdEventProbe {
    private static final String TAG = "MCCADQA";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final int MAX_EVENTS = 60;
    private AdEventProbe() {}

    private static boolean adEvent(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("adloaded") || s.contains("adfailed") || s.contains("rewarded") ||
            s.contains("onadevent") || s.contains("onadclosed") ||
            s.contains("onadopened") || s.contains("onadshowed") ||
            s.contains("onaddismissed") || s.contains("onuserearnedreward") || s.contains("onadimpression") ||
            s.equals("ad_event") || s.equals("rewarded_complete") ||
            s.equals("rewarded_closed");
    }

    private static String classify(String name, Object payload) {
        // Some plugins multiplex ad events through a single method name.
        if ("onAdEvent".equals(name) && payload instanceof Map) {
            Object event = ((Map<?, ?>) payload).get("eventName");
            if (event instanceof String && adEvent((String) event))
                return (String) event;
            return "onAdEvent";
        }
        return name;
    }

    public static void install(ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> channel = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", loader);
            XposedBridge.hookAllMethods(channel, "invokeMethod", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0 ||
                            !(param.args[0] instanceof String)) return;
                    String name = (String) param.args[0];
                    if (!adEvent(name)) return;
                    int n = EVENTS.incrementAndGet();
                    if (n > MAX_EVENTS) return;
                    Object payload = param.args.length > 1 ? param.args[1] : null;
                    XposedBridge.log(TAG + ": BRIDGE_EVENT " + classify(name, payload));
                    if (n == MAX_EVENTS) XposedBridge.log(TAG + ": LOG_LIMIT_REACHED");
                }
            });
            XposedBridge.log(TAG + ": FLUTTER_BRIDGE_PROBE_INSTALLED_READ_ONLY");
        } catch (Throwable t) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": FLUTTER_BRIDGE_PROBE_UNAVAILABLE " + t.getClass().getSimpleName());
        }
    }
}
