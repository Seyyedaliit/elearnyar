package ir.ac.uk.elearn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AppPreferences prefs = AppPreferences.getInstance(context);
            if (prefs.isLoggedIn() && prefs.isSyncEnabled()) {
                NotificationScheduler.schedulePeriodicSync(context);

                // Restore exercise alarms from cache
                String cacheJson = prefs.getCachedData();
                if (!cacheJson.isEmpty()) {
                    try {
                        JSONArray arr = new JSONArray(cacheJson);
                        List<ExerciseItem> activeList = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            Course c = Course.fromJson(arr.getJSONObject(i));
                            for (ExerciseItem ex : c.getExercises()) {
                                if (!ex.isExpired()) activeList.add(ex);
                            }
                        }
                        NotificationScheduler.scheduleExerciseReminders(context, activeList);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
