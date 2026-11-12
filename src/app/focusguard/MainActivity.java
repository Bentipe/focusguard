package app.focusguard;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private Button adminBtn, a11yBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(64, 160, 64, 64);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Focus Guard");
        title.setTextSize(30);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(0, 48, 0, 48);
        root.addView(status);

        adminBtn = new Button(this);
        adminBtn.setText("Step 1 · Protect against uninstall");
        adminBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin());
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops Focus Guard from being uninstalled from the phone.");
                startActivity(i);
            }
        });
        root.addView(adminBtn);

        a11yBtn = new Button(this);
        a11yBtn.setText("Step 2 · Turn on blocking");
        a11yBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(a11yBtn);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean adminOn = dpm().isAdminActive(admin());
        String enabled = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean a11yOn = enabled != null && enabled.contains(getPackageName() + "/");
        adminBtn.setVisibility(adminOn ? View.GONE : View.VISIBLE);
        a11yBtn.setVisibility(a11yOn || !adminOn ? View.GONE : View.VISIBLE);
        if (adminOn && a11yOn) {
            status.setText("✅ Active\n\nBlocked: social media, YouTube, TikTok, video apps, online shopping "
                + "and delivery apps and websites, and browsers other than Chrome and Firefox.\n\n"
                + "Allowed: Steam, WeChat, Strava, Movistar Plus+.\n\n"
                + "There is no off switch here on purpose. Turning it off needs a computer.");
        } else if (adminOn) {
            status.setText("Almost there. Tap Step 2, open \"Focus Guard\" under downloaded apps and switch it on.");
        } else {
            status.setText("Not active yet. Tap Step 1 and confirm.");
        }
    }

    private DevicePolicyManager dpm() {
        return (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private ComponentName admin() { return new ComponentName(this, AdminReceiver.class); }
}
