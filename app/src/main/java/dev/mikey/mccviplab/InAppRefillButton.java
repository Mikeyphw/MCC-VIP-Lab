package dev.mikey.mccviplab;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Compact button in FlutterActivity's own view, next to a discoverable diamond
 * semantics node. If Flutter does not expose that node, use a top-right fallback.
 * Root is never requested from the target UID: foreground MODULE Activity owns it.
 */
public final class InAppRefillButton {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String MARKER = "mcc_vip_lab_refill_button_personal";
    private static WeakReference<Activity> current = new WeakReference<>(null);
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
    private static View flutterView(View view) {
        ArrayDeque<View> stack = new ArrayDeque<>();
        stack.add(view);
        int limit = 90;
        while (!stack.isEmpty() && --limit > 0) {
            View node = stack.pop();
            String name = node.getClass().getName();
            if (name.equals("io.flutter.embedding.android.FlutterView")) return node;
            if (node instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) node;
                for (int i = 0; i < group.getChildCount(); i++) stack.add(group.getChildAt(i));
            }
        }
        return null;
    }
    private static boolean diamondLabel(CharSequence label) {
        if (label == null) return false;
        String s = label.toString().toLowerCase(Locale.ROOT);
        return s.contains("diamond") || s.contains("diamante") || s.contains("gem") ||
               s.contains("💎") || s.contains("♦");
    }
    private static Rect findDiamond(Activity activity, FrameLayout root) {
        View flutter = flutterView(root);
        if (flutter == null) return null;
        AccessibilityNodeProvider provider = flutter.getAccessibilityNodeProvider();
        if (provider == null) return null;
        try {
            AccessibilityNodeInfo node = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID);
            if (node == null) return null;
            ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
            pending.add(node);
            int count = 0;
            int[] position = new int[2];
            root.getLocationOnScreen(position);
            Rect usable = new Rect(0, 0, root.getWidth(), root.getHeight());
            while (!pending.isEmpty() && ++count <= 110) {
                AccessibilityNodeInfo item = pending.removeFirst();
                if (diamondLabel(item.getText()) || diamondLabel(item.getContentDescription())) {
                    Rect found = new Rect();
                    item.getBoundsInScreen(found);
                    found.offset(-position[0], -position[1]);
                    if (found.width() > 0 && found.height() > 0 && Rect.intersects(found, usable))
                        return found;
                }
                for (int i = 0; i < Math.min(item.getChildCount(), 25); i++) {
                    try {
                        AccessibilityNodeInfo child = item.getChild(i);
                        if (child != null) pending.add(child);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
            // Semantics can be disabled or be temporarily unavailable.
        }
        return null;
    }
    private static void position(Activity activity, FrameLayout root, Button button) {
        if (!button.isAttachedToWindow() || root.getWidth() <= 0) return;
        Rect rect = findDiamond(activity, root);
        int[] anchor = rect == null ? null :
                new int[]{rect.left, rect.top, rect.right, rect.bottom};
        int width = button.getWidth() > 0 ? button.getWidth() : dp(activity, 72);
        int height = button.getHeight() > 0 ? button.getHeight() : dp(activity, 40);
        int[] xy = DiamondButtonPlacement.position(root.getWidth(), root.getHeight(),
                width, height, dp(activity, 8), dp(activity, 68), anchor);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
        if (lp.leftMargin != xy[0] || lp.topMargin != xy[1]) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = xy[0];
            lp.topMargin = xy[1];
            button.setLayoutParams(lp);
        }
    }

    /** Target foreground user action -> explicit module Activity, with trusted UID sharing. */
    private static void openControl(Activity activity, boolean auto) {
        try {
            Intent i = new Intent(RootRefillCommand.ACTION_FROM_TARGET);
            i.setComponent(new ComponentName("dev.mikey.mccviplab",
                    "dev.mikey.mccviplab.RefillControlActivity"));
            i.putExtra(RootRefillCommand.EXTRA_AUTO, auto);
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setShareIdentityEnabled(true);
                activity.startActivity(i, options.toBundle());
            } else {
                activity.startActivity(i); // opens control UI, requires manual tap
            }
            XposedBridge.log("MCCREFILL: TARGET_CONTROL_ACTIVITY_OPEN_REQUESTED auto=" + auto);
        } catch (Throwable error) {
            XposedBridge.log("MCCREFILL: CONTROL_ACTIVITY_LAUNCH_FAILED=" + error.getClass().getSimpleName());
            Toast.makeText(activity, "Could not open MCC VIP Lab: " +
                    error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
    /** Automatic watcher requests are always marshalled onto the foreground Activity. */
    static boolean requestAutomatic() {
        Activity a = current.get();
        if (a == null || a.isFinishing() || !isFlutterActivity(a)) return false;
        a.runOnUiThread(() -> { if (!a.isFinishing()) openControl(a, true); });
        return true;
    }

    private static void attach(Activity activity) {
        if (!isFlutterActivity(activity)) return;
        current = new WeakReference<>(activity);
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) decor;
        if (root.findViewWithTag(MARKER) != null) return;
        Button button = new Button(activity);
        button.setTag(MARKER);
        button.setText("♦ 9");
        button.setContentDescription("Set diamonds to 9");
        button.setTextSize(13f);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(37, 66, 103)));
        button.setElevation(dp(activity, 4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, 68), dp(activity, 38), Gravity.TOP | Gravity.END);
        lp.setMargins(dp(activity, 8), dp(activity, 68), dp(activity, 8), 0);
        root.addView(button, lp);
        button.setOnClickListener(v -> openControl(activity, false));
        final Runnable reposition = new Runnable() {
            @Override public void run() {
                if (!button.isAttachedToWindow()) return;
                position(activity, root, button);
                button.postDelayed(this, 1100L);
            }
        };
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { button.removeCallbacks(reposition); }
        });
        button.post(reposition);
        XposedBridge.log("MCCREFILL: TARGET_UI_BUTTON_ADDED anchor=flutter_diamond_semantics_or_top_right");
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Activity)) return;
                    Activity a = (Activity) param.thisObject;
                    try { a.runOnUiThread(() -> attach(a)); }
                    catch (Throwable e) {
                        XposedBridge.log("MCCREFILL: UI_ATTACH_FAILED=" + e.getClass().getSimpleName());
                    }
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject == current.get()) current = new WeakReference<>(null);
                }
            });
            XposedBridge.log("MCCREFILL: TARGET_UI_HOOK_INSTALLED");
        } catch (Throwable error) {
            INSTALLED.set(false);
            XposedBridge.log("MCCREFILL: TARGET_UI_HOOK_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
