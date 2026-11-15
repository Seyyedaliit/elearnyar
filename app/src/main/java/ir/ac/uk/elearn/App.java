package ir.ac.uk.elearn;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class App extends Application {

    public static final String CHANNEL_REMINDERS = "elearn_reminders_v4";
    public static final String CHANNEL_SYNC = "elearn_sync_v4";
    public static final String CHANNEL_SYNC_PROGRESS = "elearn_sync_progress_v4";

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
                    // Clean up legacy channels if present
                    try {
                        nm.deleteNotificationChannel("elearn_reminders");
                        nm.deleteNotificationChannel("elearn_sync");
                        nm.deleteNotificationChannel("elearn_sync_progress");
                    } catch (Throwable ignored) {}

                    NotificationChannel chReminders = new NotificationChannel(
                            CHANNEL_REMINDERS,
                            "یادآورها و تمرین‌های جدید",
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    chReminders.setDescription("اطلاع‌رسانی موعد تحویل تمرین‌ها و ثبت تمرین جدید در سامانه");
                    chReminders.enableVibration(true);
                    chReminders.enableLights(true);

                    NotificationChannel chSync = new NotificationChannel(
                            CHANNEL_SYNC,
                            "نتیجه پایش سامانه",
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    chSync.setDescription("اطلاع‌رسانی نتیجه پایش سامانه و تعداد تمرین‌های فعال");
                    chSync.enableVibration(true);
                    chSync.enableLights(true);

                    NotificationChannel chSyncProgress = new NotificationChannel(
                            CHANNEL_SYNC_PROGRESS,
                            "وضعیت در حال پایش",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    chSyncProgress.setDescription("نمایش وضعیت در حال انجام پایش و بروزرسانی سامانه elearn");
                    chSyncProgress.setSound(null, null);
                    chSyncProgress.enableVibration(false);
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
