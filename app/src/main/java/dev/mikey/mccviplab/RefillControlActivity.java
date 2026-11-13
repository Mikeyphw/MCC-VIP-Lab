package dev.mikey.mccviplab;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Standalone module UI, usable even if the in-app injection isn't visible. */
public final class RefillControlActivity extends Activity {
    private TextView status;
    private Button force;
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(28), dp(20), dp(20));
        TextView title = new TextView(this);
        title.setText("MCC VIP Lab · Diamond control");
        title.setTextSize(21f);
        panel.addView(title);
        TextView details = new TextView(this);
        details.setText("Uses the successful P09 sequence: stop Meu Cronograma Capilar, set the sole local DiamondBalance file to 9, verify it, then reopen the app. Root permission is required for MCC VIP Lab. This may interrupt your current screen. Backend acceptance is not verified.");
        details.setPadding(0, dp(14), 0, dp(16));
        panel.addView(details);
        Switch auto = new Switch(this);
        auto.setText("Automatically refill at zero (restarts app)");
        auto.setChecked(AutoRefillWorker.autoEnabled(this));
        auto.setOnCheckedChangeListener((button, isChecked) -> AutoRefillWorker.setAutoEnabled(this, isChecked));
        panel.addView(auto);
        force = new Button(this);
        force.setText("Set diamonds to 9 and reopen app");
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLp.topMargin = dp(22);
        panel.addView(force, buttonLp);
        status = new TextView(this);
        status.setPadding(0, dp(15), 0, 0);
        status.setText("Last result: " + AutoRefillWorker.lastResult(this));
        panel.addView(status);
        force.setOnClickListener(v -> {
            force.setEnabled(false);
            status.setText("Requesting root; performing stop → set 9 → restart…");
            new Thread(() -> {
                String result = AutoRefillWorker.execute(getApplicationContext(), false);
                runOnUiThread(() -> {
                    status.setText("Result: " + result + "\n" + AutoRefillWorker.lastResult(this));
                    force.setEnabled(true);
                });
            }, "mcc-refill-module-ui").start();
        });
        setContentView(panel);
    }
}
