package ir.ac.uk.elearn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (NotificationScheduler.ACTION_REMINDER.equals(action)) {
            handleMilestoneReminder(context, intent);
        } else if (NotificationScheduler.ACTION_PERIODIC_SYNC.equals(action)) {
            handlePeriodicSync(context);
        }
    }

    private void handleMilestoneReminder(Context context, Intent intent) {
        String exid = intent.getStringExtra(NotificationScheduler.EXTRA_EXID);
        String title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE);
        String course = intent.getStringExtra(NotificationScheduler.EXTRA_COURSE);
        String milestone = intent.getStringExtra(NotificationScheduler.EXTRA_MILESTONE);
        String deadlineStr = intent.getStringExtra(NotificationScheduler.EXTRA_DEADLINE_STR);

        if (exid == null || milestone == null) return;

        AppPreferences prefs = AppPreferences.getInstance(context);
        String milestoneKey = exid + "_" + milestone;
        if (prefs.isMilestoneNotified(milestoneKey)) return;

        // Check if this milestone toggle is enabled
        boolean enabled = false;
        String prefix = "";
        if (NotificationScheduler.MILESTONE_3H.equals(milestone) && prefs.isNotif3Hours()) {
            enabled = true;
            prefix = "⏳ ۳ ساعت تا پایان مهلت تحویل!";
        } else if (NotificationScheduler.MILESTONE_1H.equals(milestone) && prefs.isNotif1Hour()) {
            enabled = true;
            prefix = "⚠️ فوری: تنها ۱ ساعت تا مهلت تحویل!";
        } else if (NotificationScheduler.MILESTONE_15M.equals(milestone) && prefs.isNotif15Min()) {
            enabled = true;
            prefix = "🚨 هشدار: ۱۵ دقیقه تا پایان مهلت تحویل!";
        } else if (NotificationScheduler.MILESTONE_DUE_DAY.equals(milestone) && prefs.isNotifDueDay()) {
            enabled = true;
            prefix = "📅 امروز موعد تحویل تمرین است:";
        } else if (NotificationScheduler.MILESTONE_WEEKLY.equals(milestone) && prefs.isNotifWeekly()) {
            enabled = true;
            prefix = "🗓 یادآوری هفتگی تحویل تمرین:";
        }

        if (enabled) {
            String notifTitle = prefix + " (" + course + ")";
            String persianDeadline = PersianDateHelper.toPersianDate(deadlineStr);
            String body = title + "\nمهلت: " + persianDeadline;
            int notifId = (exid + milestone).hashCode();

            NotificationScheduler.showReminderNotification(context, notifTitle, body, notifId);
            prefs.markMilestoneNotified(milestoneKey);
        }
    }

    private void handlePeriodicSync(Context context) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        if (!prefs.isLoggedIn() || !prefs.isSyncEnabled()) return;

        // If net is disconnected: stay waiting silently without notification!
        if (!NetworkHelper.isNetworkAvailable(context)) {
            NetworkSyncManager.waitForNetworkAndSync(context);
            return;
        }

        Intent serviceIntent = new Intent(context, BackgroundSyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
