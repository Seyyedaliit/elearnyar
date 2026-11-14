package ir.ac.uk.elearn;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.HashSet;
import java.util.Set;

public class AppPreferences {

    private static final String PREF_NAME = "elearn_prefs";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static AppPreferences instance;
    private final SharedPreferences prefs;

    private AppPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new AppPreferences(context);
        }
        return instance;
    }

    // Credentials (No hardcoded credentials)
    public String getUsername() {
        return prefs.getString("username", "");
    }

    public void setUsername(String username) {
        prefs.edit().putString("username", username).apply();
    }

    public String getPassword() {
        return prefs.getString("password", "");
    }

    public void setPassword(String password) {
        prefs.edit().putString("password", password).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean("is_logged_in", false);
    }

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean("is_logged_in", loggedIn).apply();
    }

    public String getStudentName() {
        return prefs.getString("student_name", "");
    }

    public void setStudentName(String name) {
        prefs.edit().putString("student_name", name).apply();
    }

    // Theme (0: System, 1: Light, 2: Dark)
    public int getThemeMode() {
        return prefs.getInt("theme_mode", THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt("theme_mode", mode).apply();
        applyTheme();
    }

    public void applyTheme() {
        int mode = getThemeMode();
        if (mode == THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    // Sync Interval & Enable
    public boolean isSyncEnabled() {
        return prefs.getBoolean("sync_enabled", true);
    }

    public void setSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean("sync_enabled", enabled).apply();
    }

    public int getSyncIntervalHours() {
        return prefs.getInt("sync_interval_hours", 3);
    }

    public void setSyncIntervalHours(int hours) {
        prefs.edit().putInt("sync_interval_hours", hours).apply();
    }

    // Notification Options (all default true)
    public boolean isNotifOnScan() {
        return prefs.getBoolean("notif_on_scan", true);
    }

    public void setNotifOnScan(boolean enabled) {
        prefs.edit().putBoolean("notif_on_scan", enabled).apply();
    }

    public boolean isNotifWeekly() {
        return prefs.getBoolean("notif_weekly", true);
    }

    public void setNotifWeekly(boolean enabled) {
        prefs.edit().putBoolean("notif_weekly", enabled).apply();
    }

    public boolean isNotifDueDay() {
        return prefs.getBoolean("notif_due_day", true);
    }

    public void setNotifDueDay(boolean enabled) {
        prefs.edit().putBoolean("notif_due_day", enabled).apply();
    }

    public boolean isNotif3Hours() {
        return prefs.getBoolean("notif_3h", true);
    }

    public void setNotif3Hours(boolean enabled) {
        prefs.edit().putBoolean("notif_3h", enabled).apply();
    }

    public boolean isNotif1Hour() {
        return prefs.getBoolean("notif_1h", true);
    }

    public void setNotif1Hour(boolean enabled) {
        prefs.edit().putBoolean("notif_1h", enabled).apply();
    }

    public boolean isNotif15Min() {
        return prefs.getBoolean("notif_15m", true);
    }

    public void setNotif15Min(boolean enabled) {
        prefs.edit().putBoolean("notif_15m", enabled).apply();
    }

    // Cache Data
    public String getCachedData() {
        return prefs.getString("cached_data_json", "");
    }

    public void setCachedData(String json) {
        prefs.edit().putString("cached_data_json", json).apply();
    }

    public long getLastSyncTime() {
        return prefs.getLong("last_sync_time", 0L);
    }

    public void setLastSyncTime(long time) {
        prefs.edit().putLong("last_sync_time", time).apply();
    }

    // Known IDs for detecting newly added files/exercises
    public Set<String> getKnownExerciseIds() {
        return prefs.getStringSet("known_exercise_ids", new HashSet<String>());
    }

    public void setKnownExerciseIds(Set<String> ids) {
        prefs.edit().putStringSet("known_exercise_ids", new HashSet<>(ids)).apply();
    }

    public Set<String> getKnownFileIds() {
        return prefs.getStringSet("known_file_ids", new HashSet<String>());
    }

    public void setKnownFileIds(Set<String> ids) {
        prefs.edit().putStringSet("known_file_ids", new HashSet<>(ids)).apply();
    }

    // Milestones already notified (e.g. "exid_3h")
    public boolean isMilestoneNotified(String key) {
        Set<String> set = prefs.getStringSet("notified_milestones", new HashSet<String>());
        return set.contains(key);
    }

    public void markMilestoneNotified(String key) {
        Set<String> set = new HashSet<>(prefs.getStringSet("notified_milestones", new HashSet<String>()));
        set.add(key);
        prefs.edit().putStringSet("notified_milestones", set).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
