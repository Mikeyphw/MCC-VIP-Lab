package dev.mikey.mccviplab;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Read-only reward amount/type observation at native callback and Flutter bridge. */
public final class RewardDetailProbe {
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final int MAX_EVENTS = 60;
    private RewardDetailProbe() {}

    private static boolean isReward(String name) {
        return "onRewardedAdUserEarnedReward".equals(name) ||
                "onRewardedInterstitialAdUserEarnedReward".equals(name);
    }

    private static void record(String stage, Object id, Object item) {
        if (EVENTS.incrementAndGet() > MAX_EVENTS) return;
        String safeId = id instanceof Number && ((Number) id).longValue() >= 0 &&
                ((Number) id).longValue() < 100000 ? String.valueOf(((Number) id).longValue()) : "unknown";
        RewardDetail details = RewardDetail.inspect(item);
        XposedBridge.log("MCCP07: " + stage + " ad_id=" + safeId + " " + details.logFields());
    }

    private static void nativeCallback(Object[] args) {
        if (args == null || args.length == 0) return;
        Object id = args[0];
        // The Android plugin normally passes the item as argument 1.
        // Unknown shapes remain UNKNOWN; no guessing or rewriting.
        Object rewardItem = args.length > 1 ? args[1] : null;
        record("SDK_REWARD", id, rewardItem);
    }

    private static void flutterBridge(Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof String) ||
                !(args[1] instanceof Map)) return;
        String method = (String) args[0];
        Map<?, ?> data = (Map<?, ?>) args[1];
        String event = "onAdEvent".equals(method) && data.get("eventName") instanceof String ?
                (String) data.get("eventName") : method;
        if (!isReward(event)) return;
        record("BRIDGE_REWARD", data.get("adId"), data.get("rewardItem"));
    }

    public static void install(ClassLoader loader) {
        int installed = 0;
        try {
            Class<?> manager = XposedHelpers.findClass(
                    "io.flutter.plugins.googlemobileads.AdInstanceManager", loader);
            for (String method : new String[]{"onRewardedAdUserEarnedReward",
                    "onRewardedInterstitialAdUserEarnedReward"}) {
                try {
                    XposedBridge.hookAllMethods(manager, method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            nativeCallback(p.args);
                        }
                    });
                    installed++;
                } catch (Throwable t) {
                    XposedBridge.log("MCCP07: SDK_HOOK_UNAVAILABLE");
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("MCCP07: SDK_MANAGER_UNAVAILABLE");
        }
        try {
            Class<?> channel = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", loader);
            XposedBridge.hookAllMethods(channel, "invokeMethod", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    flutterBridge(p.args);
                }
            });
            installed++;
        } catch (Throwable t) {
            XposedBridge.log("MCCP07: BRIDGE_HOOK_UNAVAILABLE");
        }
        XposedBridge.log("MCCP07: REWARD_DETAIL_PROBE_INSTALLED_READ_ONLY hooks=" + installed);
    }
}
