package dev.mikey.mccviplab;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Foreground module UI; executes root operation only in the module UID. */
public final class RefillControlActivity extends Activity {
    private TextView status;
    private Button force;
    private boolean running;
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private boolean trustedFromTarget(Intent intent) {
        if (intent == null || !RootRefillCommand.ACTION_FROM_TARGET.equals(intent.getAction())) return false;
        if (Build.VERSION.SDK_INT < 34) return false;
        try {
            int expected = getPackageManager().getPackageUid(RootRefillCommand.TARGET, 0);
            return getLaunchedFromUid() == expected &&
                    RootRefillCommand.TARGET.equals(getLaunchedFromPackage());
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(28), dp(20), dp(20));
        TextView title = new TextView(this);
        title.setText("MCC VIP Lab · Personal diamond control");
        title.setTextSize(20f);
        panel.addView(title);
        TextView details = new TextView(this);
        details.setText("Locally tested with Meu Cronograma Capilar 20.2609.3009. Newer app versions are allowed but not yet validated. Setting 9 force-stops the app, writes the dedicated balance file, and attempts to reopen it. No backend approval is implied.");
        details.setPadding(0, dp(14), 0, dp(16));
        panel.addView(details);
        Switch auto = new Switch(this);
        auto.setText("Automatically set 9 at zero (restarts app)");
        auto.setChecked(AutoRefillWorker.autoEnabled(this));
        auto.setOnCheckedChangeListener((button, checked) -> AutoRefillWorker.setAutoEnabled(this, checked));
        panel.addView(auto);
        force = new Button(this);
        force.setText("Set diamonds to 9 and reopen app");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        panel.addView(force, lp);
        status = new TextView(this);
        status.setPadding(0, dp(15), 0, 0);
        status.setText("Last result: " + AutoRefillWorker.lastResult(this));
        panel.addView(status);
        force.setOnClickListener(v -> startRefill(false));
        setContentView(panel);
        if (savedInstanceState == null && trustedFromTarget(getIntent())) {
            startRefill(getIntent().getBooleanExtra(RootRefillCommand.EXTRA_AUTO, false));
        }
    }

    private void startRefill(boolean auto) {
        if (running) return;
        running = true;
        force.setEnabled(false);
        status.setText("Applying P09 stop → verify → write → relaunch…");
        new Thread(() -> {
            String result = AutoRefillWorker.execute(getApplicationContext(), auto);
            runOnUiThread(() -> {
                status.setText("Result: " + result + "\n" + AutoRefillWorker.lastResult(this));
                force.setEnabled(true);
                running = false;
                if (result.startsWith("WRITE_9_VERIFIED_LAUNCH_DISPATCHED")) finish();
            });
        }, "mcc-personal-refill").start();
    }
}
