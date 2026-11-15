package ir.ac.uk.elearn;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class NotificationScheduler {

    public static final String ACTION_REMINDER = "ir.ac.uk.elearn.ACTION_REMINDER";
    public static final String ACTION_PERIODIC_SYNC = "ir.ac.uk.elearn.ACTION_PERIODIC_SYNC";

    public static final int NOTIF_ID_SYNC_PROGRESS = 7001;
    public static final int NOTIF_ID_SYNC_RESULT = 7002;

    public static final String EXTRA_EXID = "extra_exid";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_COURSE = "extra_course";
    public static final String EXTRA_MILESTONE = "extra_milestone";
    public static final String EXTRA_DEADLINE_STR = "extra_deadline_str";

    public static final String MILESTONE_SCAN = "scan";
    public static final String MILESTONE_WEEKLY = "weekly";
    public static final String MILESTONE_DUE_DAY = "due_day";
    public static final String MILESTONE_3H = "3h";
    public static final String MILESTONE_1H = "1h";
    public static final String MILESTONE_15M = "15m";

    public static void schedulePeriodicSync(Context context) {
        try {
            AppPreferences prefs = AppPreferences.getInstance(context);
            if (!prefs.isSyncEnabled() || !prefs.isLoggedIn()) {
                cancelPeriodicSync(context);
                return;
            }

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction(ACTION_PERIODIC_SYNC);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            int hours = prefs.getSyncIntervalHours();
            long intervalMs = hours * 3600 * 1000L;
            long triggerAtMs = System.currentTimeMillis() + intervalMs;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi);
            } else {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAtMs, intervalMs, pi);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void cancelPeriodicSync(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction(ACTION_PERIODIC_SYNC);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );
            am.cancel(pi);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void scheduleExerciseReminders(Context context, List<ExerciseItem> activeExercises) {
        if (activeExercises == null || activeExercises.isEmpty()) return;
        AppPreferences prefs = AppPreferences.getInstance(context);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long now = System.currentTimeMillis();

        for (ExerciseItem ex : activeExercises) {
            Date deadline = PersianDateHelper.parseDate(ex.getEndDate());
            if (deadline == null) continue;
            long deadlineMs = deadline.getTime();
            if (deadlineMs <= now) continue;

            // 1. Weekly Reminder (7 days before deadline)
            if (prefs.isNotifWeekly()) {
                long weeklyTime = deadlineMs - (7 * 24 * 3600 * 1000L);
                if (weeklyTime > now) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_WEEKLY, weeklyTime);
                }
            }

            // 2. Due Date Reminder (at 09:00 AM on the day of deadline)
            if (prefs.isNotifDueDay()) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(deadline);
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                long dueDayMorning = cal.getTimeInMillis();
                if (dueDayMorning > now && dueDayMorning < deadlineMs) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_DUE_DAY, dueDayMorning);
                } else if (deadlineMs - now > 12 * 3600 * 1000L) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_DUE_DAY, deadlineMs - (12 * 3600 * 1000L));
                }
            }

            // 3. 3 Hours before deadline
            if (prefs.isNotif3Hours()) {
                long time3h = deadlineMs - (3 * 3600 * 1000L);
                if (time3h > now) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_3H, time3h);
                }
            }

            // 4. 1 Hour before deadline
            if (prefs.isNotif1Hour()) {
                long time1h = deadlineMs - (3600 * 1000L);
                if (time1h > now) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_1H, time1h);
                }
            }

            // 5. 15 Minutes before deadline
            if (prefs.isNotif15Min()) {
                long time15m = deadlineMs - (15 * 60 * 1000L);
                if (time15m > now) {
                    scheduleSingleAlarm(context, am, ex, MILESTONE_15M, time15m);
                }
            }
        }
    }

    private static void scheduleSingleAlarm(Context context, AlarmManager am, ExerciseItem ex, String milestone, long triggerMs) {
        try {
            String milestoneKey = ex.getExid() + "_" + milestone;
            AppPreferences prefs = AppPreferences.getInstance(context);
            if (prefs.isMilestoneNotified(milestoneKey)) {
                return;
            }

            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction(ACTION_REMINDER);
            intent.putExtra(EXTRA_EXID, ex.getExid());
            intent.putExtra(EXTRA_TITLE, ex.getTitle());
            intent.putExtra(EXTRA_COURSE, ex.getCourseName());
            intent.putExtra(EXTRA_MILESTONE, milestone);
            intent.putExtra(EXTRA_DEADLINE_STR, ex.getEndDate());

            int reqCode = (ex.getExid() + milestone).hashCode();
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void showReminderNotification(Context context, String title, String body, int notifId) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(notifId, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void showSyncNotification(Context context, String title, String body, int notifId) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_SYNC)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(notifId, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static Notification buildSyncProgressNotification(Context context, String statusMessage, int current, int total) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                8888,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_SYNC_PROGRESS)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("پایش سامانه elearn")
                .setContentText(statusMessage != null ? statusMessage : "در حال پایش و بروزرسانی دروس...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);

        if (total > 0 && current >= 0) {
            builder.setProgress(total, current, false);
        } else {
            builder.setProgress(100, 0, true);
        }

        return builder.build();
    }

    public static void showSyncProgress(Context context, String statusMessage, int current, int total) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                Notification notif = buildSyncProgressNotification(context, statusMessage, current, total);
                nm.notify(NOTIF_ID_SYNC_PROGRESS, notif);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void cancelSyncProgress(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIF_ID_SYNC_PROGRESS);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void showSyncResultNotification(Context context, String title, String body) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    NOTIF_ID_SYNC_RESULT,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_SYNC)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID_SYNC_RESULT, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void showNewExerciseNotification(Context context, String title, String body, int notifId) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(notifId, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
