package app.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GuardService extends AccessibilityService {
    static final String TAG = "FocusGuard";
    private static final String SELF = "app.focusguard";
    private static final String LABEL = "focus guard";
    private static final int MAX_NODES = 2500;

    private static final Set<String> SETTINGS_PKGS = new HashSet<>(Arrays.asList(
        "com.android.settings", "com.google.android.settings", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller"));

    /** Buttons that would turn Focus Guard off or remove it (Spanish + English). */
    private static final Set<String> DANGER_WORDS = new HashSet<>(Arrays.asList(
        "desinstalar", "uninstall", "forzar detención", "forzar detencion", "force stop",
        "desactivar", "deactivate", "desactivar esta app de administración",
        "deactivate this device admin app", "borrar almacenamiento", "clear storage",
        "borrar datos", "clear data", "inhabilitar", "disable", "usar focus guard",
        "use focus guard", "aceptar", "ok"));

    private static final Set<String> ENABLE_WORDS = new HashSet<>(Arrays.asList(
        "habilitar", "enable", "activar"));

    private static final Set<String> INSTALL_WORDS = new HashSet<>(Arrays.asList(
        "instalar", "install", "actualizar", "update", "habilitar", "enable", "activar", "abrir",
        "open", "preinscribirse", "pre-register", "jugar", "play"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> otherBrowsers = new HashSet<>();
    private long lastKick;
    private String lastCheckedPkg;
    private long lastCheckAt;
    private BroadcastReceiver packageReceiver;

    @Override
    protected void onServiceConnected() {
        refreshBrowsers();
        packageReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Uri data = i.getData();
                if (data == null) return;
                String pkg = data.getSchemeSpecificPart();
                refreshBrowsers();
                if (isForbiddenApp(pkg)) removeApp(pkg);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f, Context.RECEIVER_EXPORTED);
        applyDeviceOwnerPolicies();
        Log.i(TAG, "service connected; extra browsers blocked: " + otherBrowsers);
    }

    @Override
    public void onDestroy() {
        if (packageReceiver != null) unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(SELF) || pkg.equals("com.android.systemui")) return;

        if (isForbiddenApp(pkg)) {
            kick("App blocked", false);
            return;
        }

        boolean browser = Blocklist.ALLOWED_BROWSERS.contains(pkg);
        boolean store = pkg.equals("com.android.vending");
        boolean settings = SETTINGS_PKGS.contains(pkg);
        if (!browser && !store && !settings) return;

        // Content-changed events fire constantly; check each window at most every 250 ms.
        long now = SystemClock.uptimeMillis();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && pkg.equals(lastCheckedPkg) && now - lastCheckAt < 250) return;
        lastCheckedPkg = pkg;
        lastCheckAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return;
        List<AccessibilityNodeInfo> nodes = collect(root);

        if (browser) checkBrowser(nodes);
        else if (store) checkStore(nodes);
        else checkSettings(nodes);
    }

    private void checkBrowser(List<AccessibilityNodeInfo> nodes) {
        Rect r = new Rect();
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence text = n.getText();
            if (text == null) continue;
            String id = n.getViewIdResourceName();
            String idLower = id == null ? "" : id.toLowerCase(Locale.ROOT);
            CharSequence cls = n.getClassName();
            boolean addressLike = idLower.contains("url") || idLower.contains("omnibar")
                || idLower.contains("location_bar") || idLower.contains("address")
                || idLower.contains("toolbar") || (cls != null && cls.toString().endsWith("EditText"));
            if (!addressLike) {
                // Firefox's newer toolbar has no view ids: accept URL-looking text near the top or bottom bar.
                n.getBoundsInScreen(r);
                boolean inBar = r.top < 350 || r.bottom > 2050;
                if (!inBar || n.getChildCount() > 0) continue;
            }
            String host = Blocklist.hostOf(text);
            if (Blocklist.isBlockedHost(host)) {
                Log.i(TAG, "blocked site " + host);
                kick("Site blocked: " + host, true);
                return;
            }
        }
    }

    private void checkStore(List<AccessibilityNodeInfo> nodes) {
        boolean name = false, action = false;
        for (AccessibilityNodeInfo n : nodes) {
            String t = lower(n.getText());
            String d = lower(n.getContentDescription());
            if (t != null) {
                if (Blocklist.matchesStoreName(t)) name = true;
                if (INSTALL_WORDS.contains(t)) action = true;
            }
            if (d != null && Blocklist.matchesStoreName(d)) name = true;
        }
        if (name && action) kick("Store page blocked", true);
    }

    private void checkSettings(List<AccessibilityNodeInfo> nodes) {
        boolean mentionsSelf = false, danger = false, hasSwitch = false;
        boolean mentionsSystemApp = false, enableButton = false;
        for (AccessibilityNodeInfo n : nodes) {
            String t = lower(n.getText());
            CharSequence cls = n.getClassName();
            if (cls != null) {
                String c = cls.toString();
                if (c.endsWith("Switch") || c.endsWith("SwitchCompat") || c.endsWith("SwitchMaterial")) hasSwitch = true;
            }
            if (t == null) continue;
            if (t.contains(LABEL)) mentionsSelf = true;
            if (DANGER_WORDS.contains(t)) danger = true;
            if (ENABLE_WORDS.contains(t)) enableButton = true;
            for (String s : Blocklist.SYSTEM_APP_LABELS) if (t.equals(s)) mentionsSystemApp = true;
        }
        if (mentionsSelf && (danger || hasSwitch) && isAdminActive()) {
            kick("Focus Guard is protected", true);
        } else if (mentionsSystemApp && enableButton) {
            kick("That app stays disabled", true);
        }
    }

    // --- actions -----------------------------------------------------------------------------

    private void kick(final String why, boolean backFirst) {
        long now = SystemClock.uptimeMillis();
        if (now - lastKick < 700) return;
        lastKick = now;
        if (backFirst) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(new Runnable() {
                @Override public void run() { performGlobalAction(GLOBAL_ACTION_HOME); }
            }, 250);
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        handler.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(GuardService.this, "Focus Guard: " + why, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeApp(String pkg) {
        Log.i(TAG, "forbidden app installed: " + pkg);
        if (isDeviceOwner()) {
            Intent i = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
            getPackageManager().getPackageInstaller().uninstall(pkg, pi.getIntentSender());
            return;
        }
        Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        del.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(del); } catch (Exception e) { Log.w(TAG, "uninstall prompt failed", e); }
    }

    // --- helpers -----------------------------------------------------------------------------

    private boolean isForbiddenApp(String pkg) {
        return Blocklist.isBlockedPackage(pkg) || otherBrowsers.contains(pkg);
    }

    private void refreshBrowsers() {
        otherBrowsers.clear();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://zz-probe-4821.example/x"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (!Blocklist.ALLOWED_BROWSERS.contains(pkg) && !pkg.equals(SELF)) otherBrowsers.add(pkg);
        }
    }

    private List<AccessibilityNodeInfo> collect(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty() && out.size() < MAX_NODES) {
            AccessibilityNodeInfo n = q.poll();
            out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return out;
    }

    private static String lower(CharSequence cs) {
        return cs == null ? null : cs.toString().trim().toLowerCase(Locale.ROOT);
    }

    private DevicePolicyManager dpm() {
        return (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private ComponentName admin() { return new ComponentName(this, AdminReceiver.class); }

    private boolean isAdminActive() { return dpm().isAdminActive(admin()); }

    private boolean isDeviceOwner() { return dpm().isDeviceOwnerApp(SELF); }

    /** Only applies if Focus Guard is ever made device owner (requires a phone with no accounts). */
    private void applyDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return;
        DevicePolicyManager d = dpm();
        ComponentName a = admin();
        try {
            d.setUninstallBlocked(a, SELF, true);
            d.addUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT);
            for (String pkg : Blocklist.PACKAGES) d.setApplicationHidden(a, pkg, true);
        } catch (Exception e) {
            Log.w(TAG, "device owner policy failed", e);
        }
    }
}
