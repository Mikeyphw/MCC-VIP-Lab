package dev.mikey.mccviplab;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** A button attached to the target Flutter Activity (no overlay permission). */
public final class InAppRefillButton {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String MARKER = "mcc_vip_lab_refill_button_v2";
    private InAppRefillButton() {}

    private static boolean isFlutterActivity(Activity activity) {
        for (Class<?> c = activity.getClass(); c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if ("io.flutter.embedding.android.FlutterActivity".equals(name) ||
                "io.flutter.embedding.android.FlutterFragmentActivity".equals(name)) return true;
        }
        return false;
    }
    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }
    private static void attach(Activity activity) {
        if (!isFlutterActivity(activity)) return; // Never cover a rewarded ad Activity.
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) decor;
        if (root.findViewWithTag(MARKER) != null) return;
        Button button = new Button(activity);
        button.setTag(MARKER);
        button.setText("♦ Set 9");
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(37, 66, 103)));
        button.setElevation(dp(activity, 8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(dp(activity, 12), dp(activity, 12), dp(activity, 14), dp(activity, 78));
        root.addView(button, lp);
        button.setOnClickListener(v -> {
            button.setEnabled(false);
            Toast.makeText(activity, "Setting 9 diamonds and restarting the app…", Toast.LENGTH_SHORT).show();
            Thread sender = new Thread(() -> {
                try {
                    Bundle result = activity.getContentResolver().call(
                            Uri.parse("content://" + RootRefillCommand.PROVIDER_AUTHORITY),
                            "MANUAL_SET_9", null, null);
                    String status = result == null ? "NO_REPLY" : result.getString("result", "NO_REPLY");
                    // If successful the target Activity has already been stopped and restarted.
                    if (!"APPLIED".equals(status)) activity.runOnUiThread(() -> {
                        button.setEnabled(true);
                        Toast.makeText(activity, "Refill: " + status + ". Open MCC VIP Lab for details.", Toast.LENGTH_LONG).show();
                    });
                } catch (Throwable error) {
                    activity.runOnUiThread(() -> {
                        button.setEnabled(true);
                        Toast.makeText(activity, "Open MCC VIP Lab and grant root access.", Toast.LENGTH_LONG).show();
                    });
                    XposedBridge.log("MCCREFILL: IN_APP_BUTTON_PROVIDER_FAILED=" + error.getClass().getSimpleName());
                }
            }, "mcc-refill-button-request");
            sender.setDaemon(true);
            sender.start();
        });
        XposedBridge.log("MCCREFILL: TARGET_UI_BUTTON_ADDED force_set_9_and_restart=true");
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Activity)) return;
                    Activity activity = (Activity) param.thisObject;
                    try { activity.runOnUiThread(() -> attach(activity)); }
                    catch (Throwable e) { XposedBridge.log("MCCREFILL: UI_ATTACH_FAILED=" + e.getClass().getSimpleName()); }
                }
            });
            XposedBridge.log("MCCREFILL: TARGET_UI_HOOK_INSTALLED");
        } catch (Throwable error) {
            INSTALLED.set(false);
            XposedBridge.log("MCCREFILL: TARGET_UI_HOOK_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
