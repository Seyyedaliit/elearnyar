package ir.ac.uk.elearn;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class App extends Application {

    public static final String CHANNEL_REMINDERS = "elearn_reminders";
    public static final String CHANNEL_SYNC = "elearn_sync";
    public static final String CHANNEL_SYNC_PROGRESS = "elearn_sync_progress";

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        try {
            AppPreferences prefs = AppPreferences.getInstance(this);
            prefs.applyTheme();

            createNotificationChannels();

            if (prefs.isLoggedIn() && prefs.isSyncEnabled()) {
                NotificationScheduler.schedulePeriodicSync(this);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static App getInstance() {
        return instance;
    }

    private void createNotificationChannels() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel chReminders = new NotificationChannel(
                            CHANNEL_REMINDERS,
                            "یادآور تمرین‌های باهنر",
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    chReminders.setDescription("اطلاع‌رسانی موعد تحویل تمرین‌ها و یادآورهای زمان‌بندی شده");
                    chReminders.enableVibration(true);
                    chReminders.enableLights(true);

                    NotificationChannel chSync = new NotificationChannel(
                            CHANNEL_SYNC,
                            "پایش دوره‌ای و فایل‌های جدید",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    chSync.setDescription("اطلاع‌رسانی فایل‌های آموزشی و تمرین‌های اضافه شده جدید در پایش دوره‌ای");
                    chSync.enableVibration(true);

                    NotificationChannel chSyncProgress = new NotificationChannel(
                            CHANNEL_SYNC_PROGRESS,
                            "وضعیت پایش خودکار",
                            NotificationManager.IMPORTANCE_LOW
                    );
                    chSyncProgress.setDescription("نمایش وضعیت در حال انجام پایش سامانه elearn");
                    chSyncProgress.setShowBadge(false);

                    nm.createNotificationChannel(chReminders);
                    nm.createNotificationChannel(chSync);
                    nm.createNotificationChannel(chSyncProgress);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
