package dev.mikey.mccviplab;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Scoped banner-only Android view hiding. Rewarded/interstitial SDK paths stay untouched. */
public final class NativeBannerProbe {
    private static final Set<Class<?>> WRAPPED = new HashSet<>();
    private static final String MODE_SETTING = "mcc_lab_banner_mode";
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private NativeBannerProbe() {}

    private static boolean enabled(Context ctx) {
        return CosmeticControls.enabled(ctx, MODE_SETTING);
    }
    private static void note(String tag) {
        if (EVENTS.incrementAndGet() < 45) XposedBridge.log("MCCBANNER: " + tag);
    }
    private static void hideIfRequested(Context ctx, Object obj) {
        if (!(obj instanceof View)) return;
        if (enabled(ctx)) {
            ((View)obj).setVisibility(View.GONE);
            note("NATIVE_VIEW_HIDDEN_LOCAL_ONLY");
        } else note("NATIVE_VIEW_OBSERVED");
    }
    private static void observePlatformView(final Context ctx, Object platformView) {
        if (platformView == null) return;
        try {
            Method m = platformView.getClass().getMethod("getView");
            hideIfRequested(ctx, m.invoke(platformView));
            synchronized (WRAPPED) {
                if (!WRAPPED.add(platformView.getClass())) return;
            }
            XposedBridge.hookAllMethods(platformView.getClass(), "getView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    hideIfRequested(ctx, p.getResult());
                }
            });
        } catch (Throwable error) {
            note("PLATFORM_VIEW_UNAVAILABLE=" + error.getClass().getSimpleName());
        }
    }
    private static void platformHooks(ClassLoader loader, final Context ctx, String className) {
        try {
            Class<?> klass = XposedHelpers.findClass(className, loader);
            XposedBridge.hookAllMethods(klass, "getPlatformView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    observePlatformView(ctx, p.getResult());
                }
            });
            note("BANNER_PLATFORM_HOOK_INSTALLED=" + klass.getSimpleName());
        } catch (Throwable error) {
            note("BANNER_PLATFORM_HOOK_UNAVAILABLE=" + error.getClass().getSimpleName());
        }
    }
    private static void nativeBannerHooks(ClassLoader loader, final Context ctx, String className) {
        try {
            Class<?> klass = XposedHelpers.findClass(className, loader);
            XposedBridge.hookAllConstructors(klass, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    hideIfRequested(ctx, p.thisObject);
                }
            });
            note("BANNER_NATIVE_HOOK_INSTALLED=" + klass.getSimpleName());
        } catch (Throwable error) {
            note("BANNER_NATIVE_HOOK_UNAVAILABLE=" + error.getClass().getSimpleName());
        }
    }
    public static void install(ClassLoader loader, final Context ctx) {
        for (String name : new String[]{
                "io.flutter.plugins.googlemobileads.FlutterBannerAd",
                "io.flutter.plugins.googlemobileads.FlutterAdManagerBannerAd"}) {
            platformHooks(loader, ctx, name);
        }
        for (String name : new String[]{
                "com.google.android.gms.ads.AdView",
                "com.google.android.gms.ads.admanager.AdManagerAdView",
                "com.facebook.ads.AdView"}) {
            nativeBannerHooks(loader, ctx, name);
        }
        note("BANNER_PROBE_INSTALLED mode=" + (enabled(ctx) ? "hide" : "observe") +
                " banner_only=true rewarded_unchanged=true");
    }
}
