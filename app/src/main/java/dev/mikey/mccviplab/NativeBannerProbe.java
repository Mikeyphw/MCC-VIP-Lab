package dev.mikey.mccviplab;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional local UI test: hide native banner views, never fake ad callbacks. */
public final class NativeBannerProbe {
    private static final Set<Class<?>> WRAPPED = new HashSet<>();
    private NativeBannerProbe() {}

    private static boolean enabled(Context ctx) {
        try {
            return "hide".equals(Settings.Global.getString(ctx.getContentResolver(), "mcc_lab_banner_mode"));
        } catch (Throwable ignored) { return false; }
    }

    private static void hideIfRequested(Context ctx, Object obj) {
        if (!(obj instanceof View)) return;
        View v = (View) obj;
        if (enabled(ctx)) {
            v.setVisibility(View.GONE);
            XposedBridge.log("MCCBANNER: NATIVE_VIEW_HIDDEN_LOCAL_ONLY");
        } else XposedBridge.log("MCCBANNER: NATIVE_VIEW_OBSERVED");
    }

    private static void observePlatformView(final Context ctx, Object platformView) {
        if (platformView == null) return;
        try {
            Method m = platformView.getClass().getMethod("getView");
            Object view = m.invoke(platformView);
            hideIfRequested(ctx, view);
            synchronized (WRAPPED) {
                if (!WRAPPED.add(platformView.getClass())) return;
            }
            XposedBridge.hookAllMethods(platformView.getClass(), "getView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    hideIfRequested(ctx,p.getResult());
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("MCCBANNER: PLATFORM_VIEW_UNAVAILABLE " + error.getClass().getSimpleName());
        }
    }

    public static void install(ClassLoader loader, final Context ctx) {
        int hooks = 0;
        for (String cls : new String[]{
                "io.flutter.plugins.googlemobileads.FlutterBannerAd",
                "io.flutter.plugins.googlemobileads.FlutterAdManagerBannerAd"}) {
            try {
                Class<?> klass = XposedHelpers.findClass(cls, loader);
                XposedBridge.hookAllMethods(klass, "getPlatformView", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        observePlatformView(ctx,p.getResult());
                    }
                });
                hooks++;
            } catch (Throwable error) {
                XposedBridge.log("MCCBANNER: HOOK_UNAVAILABLE " + error.getClass().getSimpleName());
            }
        }
        XposedBridge.log("MCCBANNER: BANNER_PROBE_INSTALLED hooks=" + hooks +
            " mode=" + (enabled(ctx) ? "hide" : "observe"));
    }
}
