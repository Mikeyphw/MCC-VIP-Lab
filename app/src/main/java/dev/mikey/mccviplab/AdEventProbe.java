package dev.mikey.mccviplab;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Read-only Android -> Flutter bridge observation; no simulated ads or credits. */
public final class AdEventProbe {
    private static final String TAG = "MCCADQA";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger GROUPS = new AtomicInteger();
    private static final int MAX_GROUPS = 80;
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final Map<String, Integer> COLLAPSED = new HashMap<>();
    private AdEventProbe() {}

    private static boolean adEvent(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("adloaded") || s.contains("adfailed") || s.contains("rewarded") ||
                s.contains("onadevent") || s.contains("onadclosed") || s.contains("onadopened") ||
                s.contains("onadshowed") || s.contains("onaddismissed") ||
                s.contains("onuserearnedreward") || s.contains("onadimpression") ||
                s.equals("ad_event") || s.equals("rewarded_complete") || s.equals("rewarded_closed");
    }

    private static String classify(String name, Object payload) {
        if ("onAdEvent".equals(name) && payload instanceof Map) {
            Object event = ((Map<?, ?>) payload).get("eventName");
            if (event instanceof String && adEvent((String) event)) return (String) event;
        }
        return name;
    }

    /** A group is a time-window observation, NOT a distinct ad request. */
    public static synchronized int coalesce(String event, long nowMs) {
        Long previous = LAST.get(event);
        if (previous != null && nowMs >= previous && nowMs - previous < 350L) {
            COLLAPSED.put(event, COLLAPSED.containsKey(event) ? COLLAPSED.get(event) + 1 : 1);
            return -1;
        }
        int suppressed = COLLAPSED.containsKey(event) ? COLLAPSED.remove(event) : 0;
        LAST.put(event, nowMs);
        if (LAST.size() > 50) { LAST.clear(); COLLAPSED.clear(); }
        return suppressed;
    }

    public static void install(ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> channel = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", loader);
            XposedBridge.hookAllMethods(channel, "invokeMethod", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof String)) return;
                    String method = (String) param.args[0];
                    if (!adEvent(method)) return;
                    Object payload = param.args.length > 1 ? param.args[1] : null;
                    String event = classify(method, payload);
                    int folded = coalesce(event, System.currentTimeMillis());
                    if (folded < 0) return;
                    int n = GROUPS.incrementAndGet();
                    if (n > MAX_GROUPS) return;
                    XposedBridge.log(TAG + ": BRIDGE_GROUP " + event + " prior_duplicates_collapsed=" + folded);
                    if (n == MAX_GROUPS) XposedBridge.log(TAG + ": GROUP_LOG_LIMIT_REACHED");
                }
            });
            XposedBridge.log(TAG + ": FLUTTER_BRIDGE_PROBE_INSTALLED_READ_ONLY grouped_350ms");
        } catch (Throwable t) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": FLUTTER_BRIDGE_PROBE_UNAVAILABLE " + t.getClass().getSimpleName());
        }
    }
}
