package dev.mikey.mccviplab;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Read-only per-ad-instance observer: no SDK callback invocation or alteration. */
public final class AdInstanceTrace {
    private static final Map<Integer, String> TYPES = new HashMap<>();
    private static final Map<Integer, Integer> EVENTS_BY_ID = new HashMap<>();
    private static final int LOADED = 1, FAILED = 2, SHOWN = 4, IMPRESSION = 8, REWARD = 16;
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static final int MAX_EVENTS = 180;
    private AdInstanceTrace() {}

    private static synchronized void recordType(Object[] args) {
        if (args == null || args.length < 2 || !(args[1] instanceof Integer)) return;
        String cls = args[0] == null ? "unknown" : args[0].getClass().getSimpleName();
        String type = cls.contains("Banner") ? "banner" :
            cls.contains("Rewarded") ? "rewarded" :
            cls.contains("Interstitial") ? "interstitial" : "other";
        int id = (Integer) args[1];
        if (TYPES.size() < 300) {
            TYPES.put(id, type);
            EVENTS_BY_ID.put(id, 0);
        }
        log("TRACK", id, type);
    }

    private static void log(String event, int id, String type) {
        if (EVENTS.incrementAndGet() > MAX_EVENTS) return;
        XposedBridge.log("MCCADID: " + event + " ad_id=" + id + " type=" + type);
    }

    /** Observes callbacks; absence of other SDK events does not prove fraud. */
    private static synchronized void observe(String event, Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Integer)) return;
        int id = (Integer) args[0];
        String type = TYPES.containsKey(id) ? TYPES.get(id) : "unknown";
        int before = EVENTS_BY_ID.containsKey(id) ? EVENTS_BY_ID.get(id) : 0;
        log(event, id, type);
        int mask = 0;
        if ("LOAD_OK".equals(event)) mask = LOADED;
        else if ("LOAD_FAILED".equals(event)) mask = FAILED;
        else if ("FULLSCREEN_SHOW".equals(event)) mask = SHOWN;
        else if ("IMPRESSION".equals(event)) mask = IMPRESSION;
        else if ("REWARD_CALLBACK".equals(event) || "REWARD_INTERSTITIAL_CALLBACK".equals(event)) {
            mask = REWARD;
            String state = (before & FAILED) != 0 ? "failed_seen" :
                (before & IMPRESSION) != 0 ? "impression_seen" :
                (before & SHOWN) != 0 ? "fullscreen_seen" : "no_prior_show_observed";
            log("REWARD_CONTEXT_" + state.toUpperCase(), id, type);
        }
        if (EVENTS_BY_ID.size() < 300 || EVENTS_BY_ID.containsKey(id))
            EVENTS_BY_ID.put(id, before | mask);
    }

    public static void install(ClassLoader loader) {
        try {
            Class<?> manager = XposedHelpers.findClass(
                "io.flutter.plugins.googlemobileads.AdInstanceManager", loader);
            XposedBridge.hookAllMethods(manager, "trackAd", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { recordType(p.args); }
            });
            String[][] methods = {
                {"onAdFailedToLoad", "LOAD_FAILED"},
                {"onAdLoaded", "LOAD_OK"},
                {"onAdImpression", "IMPRESSION"},
                {"onRewardedAdUserEarnedReward", "REWARD_CALLBACK"},
                {"onRewardedInterstitialAdUserEarnedReward", "REWARD_INTERSTITIAL_CALLBACK"},
                {"onAdShowedFullScreenContent", "FULLSCREEN_SHOW"},
                {"onFailedToShowFullScreenContent", "FULLSCREEN_FAILED"},
                {"onAdDismissedFullScreenContent", "FULLSCREEN_DISMISSED"}
            };
            for (String[] method : methods) {
                final String event = method[1];
                try {
                    XposedBridge.hookAllMethods(manager, method[0], new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) { observe(event, p.args); }
                    });
                } catch (Throwable error) {
                    XposedBridge.log("MCCADID: HOOK_UNAVAILABLE " + event);
                }
            }
            XposedBridge.log("MCCADID: AD_INSTANCE_TRACE_INSTALLED_READ_ONLY");
        } catch (Throwable error) {
            XposedBridge.log("MCCADID: TRACE_UNAVAILABLE " + error.getClass().getSimpleName());
        }
    }
}
