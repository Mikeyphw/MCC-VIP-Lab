package dev.mikey.mccviplab;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Local, scoped QA module: never intercepts billing or writes subscription
 * records. Note the app itself can decide to synchronize a spoofed result.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "MCCVIPLAB";
    private static final String TARGET = "br.art.code.meucronogramacapilar";
    private static final String EXPECTED_VERSION = "20.2609.3009";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean PATCH_TOAST = new AtomicBoolean(false);

    private static void log(String s) { XposedBridge.log(TAG+": "+s); }

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;
        try {
            // Application.attach precedes Flutter plugin initialization.
            XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Context ctx=(Context)param.args[0];
                    try {
                        PackageInfo pi=ctx.getPackageManager().getPackageInfo(TARGET,0);
                        if (!EXPECTED_VERSION.equals(pi.versionName)) {
                            log("REFUSED_VERSION: expected="+EXPECTED_VERSION+" actual="+pi.versionName);
                            return;
                        }
                        if (INSTALLED.compareAndSet(false,true))
                            install(lpparam.classLoader,ctx);
                    } catch (Throwable ex) { log("ATTACH_ERROR "+ex.getClass().getSimpleName()); }
                }
            });
            log("TARGET_PROCESS package="+TARGET);
        } catch (Throwable t) { log("INIT_ERROR "+t.getClass().getSimpleName()); }
    }

    private static void showPatched(Context ctx) {
        if (!PATCH_TOAST.compareAndSet(false,true)) return;
        try {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx,"MCC VIP Lab: entitlement spoofed locally",Toast.LENGTH_LONG).show());
        } catch (Throwable ignored) {}
    }

    private static void install(ClassLoader loader, final Context appContext) {
        AdEventProbe.install(loader);
        LocalStateProbe.install();
        DiamondProbe.install(appContext);
        AdInstanceTrace.install(loader);
        RewardDetailProbe.install(loader);
        DiamondFileIOTrace.install();
        AutoRefillWatcher.install(appContext);
        NativeBannerProbe.install(loader, appContext);
        PurchaseCallProbe.install(loader);
        try {
            Class<?> mapper = XposedHelpers.findClass(
                "com.revenuecat.purchases.hybridcommon.mappers.CustomerInfoMapperKt", loader);
            XposedBridge.hookAllMethods(mapper,"map",new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object raw = param.getResult();
                        XposedBridge.log("MCCVIPSTATE: MAPPER_BEFORE " + VipStateProbe.describe(raw));
                        Map<String,Object> changed=VipPayload.patch(raw);
                        if(changed==null) return;
                        param.setResult(changed);
                        XposedBridge.log("MCCVIPSTATE: MAPPER_AFTER " + VipStateProbe.describe(changed));
                        log("PATCH_MAPPER_PASS entitlement="+VipPayload.ENTITLEMENT);
                        showPatched(appContext);
                    } catch(Throwable t) { log("PATCH_MAPPER_ERROR "+t.getClass().getSimpleName()); }
                }
            });
            log("HOOK_MAPPER_INSTALLED");
        } catch(Throwable t) { log("HOOK_MAPPER_FAILED "+t.getClass().getSimpleName()); }
        try {
            Class<?> plugin = XposedHelpers.findClass("com.revenuecat.purchases_flutter.PurchasesFlutterPlugin",loader);
            Class<?> result = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel$Result",loader);
            XposedHelpers.findAndHookMethod(plugin,"getCustomerInfo",result,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    final Object original = param.args[0];
                    try {
                        // Wrap asynchronous Result; do not invent a purchase/billing success.
                        Object wrapper = Proxy.newProxyInstance(result.getClassLoader(),new Class[]{result},
                            new InvocationHandler() {
                                @Override public Object invoke(Object proxy,Method m,Object[] args) throws Throwable {
                                    String method=m.getName();
                                    if ("success".equals(method) && args!=null && args.length==1) {
                                        XposedBridge.log("MCCVIPSTATE: CUSTOMER_INFO_BEFORE " + VipStateProbe.describe(args[0]));
                                        Map<String,Object> patched=VipPayload.patch(args[0]);
                                        if (patched!=null) {
                                            args[0]=patched;
                                            XposedBridge.log("MCCVIPSTATE: CUSTOMER_INFO_AFTER " + VipStateProbe.describe(patched));
                                            log("PATCH_CUSTOMER_INFO_PASS entitlement="+VipPayload.ENTITLEMENT);
                                            showPatched(appContext);
                                        } else { log("PATCH_CUSTOMER_INFO_BAD_SHAPE"); }
                                    } else if ("error".equals(method)) {
                                        log("CUSTOMER_INFO_SDK_ERROR (not rewritten)");
                                    }
                                    try { return m.invoke(original,args); }
                                    catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                                }
                            });
                        param.args[0]=wrapper;
                    } catch(Throwable t) { log("RESULT_WRAP_FAILED "+t.getClass().getSimpleName()); }
                }
            });
            log("HOOK_GET_CUSTOMER_INFO_INSTALLED");
        } catch(Throwable t) { log("HOOK_GET_CUSTOMER_INFO_FAILED "+t.getClass().getSimpleName()); }
    }
}
