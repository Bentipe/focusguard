package app.focusguard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Being an active device admin stops the app from being uninstalled from the phone itself. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Focus Guard keeps distraction apps off this phone. Turning it off needs a computer.";
    }
}
