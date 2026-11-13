package dev.mikey.mccviplab;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * P11 best-effort visual suppression for exact premium promotional labels.
 * Flutter renders its own UI; this masks accessible virtual-node bounds only.
 * Missing semantics are an explicit no-op; there is no broad Flutter interception.
 */
public final class PremiumUpsellOverlay {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final WeakHashMap<Activity, Tracker> TRACKERS = new WeakHashMap<>();
    private static final long UPDATE_MS = 1500L;
    private static final String MODE_SETTING = "mcc_lab_upsell_mode";
    private static int logs;
    private PremiumUpsellOverlay() {}

    private static void note(String message) {
        if (logs++ < 45) XposedBridge.log("MCCUPSELL: " + message);
    }
    private static boolean flutterActivity(Activity activity) {
        for (Class<?> c = activity.getClass(); c != null; c = c.getSuperclass()) {
            if ("io.flutter.embedding.android.FlutterActivity".equals(c.getName()) ||
                    "io.flutter.embedding.android.FlutterFragmentActivity".equals(c.getName())) return true;
        }
        return false;
    }
    private static View findFlutter(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && ++visited < 90) {
            View view = queue.removeFirst();
            if ("io.flutter.embedding.android.FlutterView".equals(view.getClass().getName())) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }
    private static int dp(Activity activity, int value) {
        return (int)(value * activity.getResources().getDisplayMetrics().density + .5f);
    }
    private static ArrayList<Rect> findLabels(Activity activity, FrameLayout root) {
        ArrayList<Rect> matches = new ArrayList<>();
        View flutter = findFlutter(root);
        if (flutter == null) return matches;
        AccessibilityNodeProvider provider = flutter.getAccessibilityNodeProvider();
        if (provider == null) return matches;
        try {
            AccessibilityNodeInfo start = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID);
            if (start == null) return matches;
            ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
            pending.add(start);
            int visited = 0;
            int[] pos = new int[2];
            root.getLocationOnScreen(pos);
            Rect container = new Rect(0, 0, root.getWidth(), root.getHeight());
            while (!pending.isEmpty() && ++visited <= 160 && matches.size() < 8) {
                AccessibilityNodeInfo node = pending.removeFirst();
                if (CosmeticPolicy.isUpsell(node.getText()) ||
                        CosmeticPolicy.isUpsell(node.getContentDescription())) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    bounds.offset(-pos[0], -pos[1]);
                    if (bounds.intersect(container) &&
                            bounds.width() >= dp(activity, 28) &&
                            bounds.height() >= dp(activity, 8) &&
                            bounds.height() <= dp(activity, 110) &&
                            bounds.width() <= (root.getWidth() * 9 / 10)) {
                        boolean duplicate = false;
                        for (Rect r : matches) if (Rect.intersects(r, bounds)) { duplicate = true; break; }
                        if (!duplicate) matches.add(bounds);
                    }
                }
                for (int i = 0; i < Math.min(node.getChildCount(), 24); i++) {
                    try {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) pending.addLast(child);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable error) {
            note("SEMANTICS_QUERY_UNAVAILABLE=" + error.getClass().getSimpleName());
        }
        return matches;
    }
    private static int backdropColor(Activity activity, FrameLayout root) {
        if (root.getBackground() instanceof ColorDrawable) {
            int color = ((ColorDrawable)root.getBackground()).getColor();
            if (Color.alpha(color) >= 245) return color;
        }
        int mode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? Color.BLACK : Color.WHITE;
    }
    private static final class Tracker {
        final WeakReference<Activity> reference;
        final FrameLayout root;
        final FrameLayout mask;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable tick;
        boolean active = true;
        int oldCount = -1;
        final ArrayList<Rect> previous = new ArrayList<>();
        Tracker(Activity activity, FrameLayout container) {
            reference = new WeakReference<>(activity);
            root = container;
            mask = new FrameLayout(activity);
            mask.setClickable(false);
            mask.setFocusable(false);
            mask.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(mask, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            tick = new Runnable() {
                @Override public void run() {
                    Activity a = reference.get();
                    if (!active || a == null || a.isFinishing()) return;
                    update(a);
                    handler.postDelayed(this, UPDATE_MS);
                }
            };
            handler.post(tick);
        }
        void update(Activity activity) {
            if (!CosmeticControls.enabled(activity, MODE_SETTING)) {
                mask.removeAllViews();
                previous.clear();
                oldCount = 0;
                return;
            }
            if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
            ArrayList<Rect> targets = findLabels(activity, root);
            if (previous.size() == targets.size()) {
                boolean unchanged = true;
                for (int i = 0; i < targets.size(); i++) {
                    if (!previous.get(i).equals(targets.get(i))) { unchanged = false; break; }
                }
                if (unchanged) return;
            }
            previous.clear();
            for (Rect target : targets) previous.add(new Rect(target));
            mask.removeAllViews();
            int color = backdropColor(activity, root);
            for (Rect rect : targets) {
                View cover = new View(activity);
                cover.setBackgroundColor(color);
                cover.setClickable(true); // Also prevent activating the obscured CTA.
                cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        rect.width(), rect.height(), Gravity.TOP | Gravity.START);
                lp.leftMargin = rect.left;
                lp.topMargin = rect.top;
                mask.addView(cover, lp);
            }
            if (oldCount != targets.size()) {
                oldCount = targets.size();
                note("VISUAL_PATCH_COUNT=" + oldCount + " only_exact_promo_text=true " +
                        "no_flutter_layout_reflow=true");
            }
        }
        void stop() {
            active = false;
            handler.removeCallbacks(tick);
            if (mask.getParent() == root) root.removeView(mask);
        }
    }
    private static void start(Activity activity) {
        if (!flutterActivity(activity)) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        synchronized (TRACKERS) {
            Tracker prior = TRACKERS.get(activity);
            if (prior != null) prior.stop();
            TRACKERS.put(activity, new Tracker(activity, (FrameLayout) decor));
        }
    }
    private static void stop(Activity activity) {
        synchronized (TRACKERS) {
            Tracker tracker = TRACKERS.remove(activity);
            if (tracker != null) tracker.stop();
        }
    }
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof Activity) {
                        Activity activity = (Activity)p.thisObject;
                        activity.runOnUiThread(() -> start(activity));
                    }
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof Activity) stop((Activity)p.thisObject);
                }
            });
            note("EXACT_UPSELL_SEMANTICS_PROBE_INSTALLED default_hide=true " +
                    "rewarded_ads_untouched=true");
        } catch (Throwable error) {
            INSTALLED.set(false);
            note("HOOK_FAILED=" + error.getClass().getSimpleName());
        }
    }
}
