package dev.melonwatch;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashSet;
import java.util.Set;

final class AppSettings {
    static final String SUPPORTED_PACKAGE = "com.iloen.melon";
    static final String[] SUPPORTED_PACKAGES = {SUPPORTED_PACKAGE, "com.ktmusic.geniemusic"};
    static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (!p.getBoolean("defaults_v3", false)) {
            SharedPreferences.Editor edit = p.edit().putBoolean("defaults_v3", true);
            if (p.getInt("interval_seconds", 30) == 30) edit.putInt("interval_seconds", 2);
            edit.apply();
        }
        return p;
    }
    static int delay(Context c) { return prefs(c).getInt("delay_seconds", 10); }
    static int interval(Context c) { return prefs(c).getInt("interval_seconds", 2); }
    static int theme(Context c) { return prefs(c).getInt("theme", 0); }
    static int maxVibrations(Context c) { return prefs(c).getInt("max_vibrations", 20); }
    static boolean pauseForRecording(Context c) { return prefs(c).getBoolean("pause_for_recording", false); }
    static boolean pauseForCalls(Context c) { return prefs(c).getBoolean("pause_for_calls", true); }
    static boolean pauseForCamera(Context c) { return prefs(c).getBoolean("pause_for_camera", true); }
    static String targetName(Context c) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String pkg : targetPackages(c)) names.add(appName(c, pkg));
        return android.text.TextUtils.join(", ", names);
    }
    static String appName(Context c, String pkg) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return SUPPORTED_PACKAGE.equals(pkg) ? "멜론 (미설치)" : "지니뮤직 (미설치)";
        }
    }
    static Set<String> targetPackages(Context c) {
        SharedPreferences p = prefs(c);
        Set<String> saved = p.getStringSet("target_packages", null);
        Set<String> selected = new LinkedHashSet<>();
        if (saved != null) {
            for (String pkg : SUPPORTED_PACKAGES) if (saved.contains(pkg)) selected.add(pkg);
        } else {
            String legacy = p.getString("target_package", SUPPORTED_PACKAGE);
            for (String pkg : SUPPORTED_PACKAGES) if (pkg.equals(legacy)) selected.add(pkg);
        }
        if (selected.isEmpty()) selected.add(SUPPORTED_PACKAGE);
        return selected;
    }
    static void saveTargets(Context c, Set<String> chosen) {
        Set<String> selected = new LinkedHashSet<>();
        for (String pkg : SUPPORTED_PACKAGES) if (chosen.contains(pkg)) selected.add(pkg);
        if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one app");
        prefs(c).edit().putStringSet("target_packages", selected).apply();
    }
}
