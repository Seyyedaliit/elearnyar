package ir.ac.uk.elearn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        // Schedule next sync in chain
        NotificationScheduler.schedulePeriodicSync(context);

        new Thread(() -> {
            try {
                String username = prefs.getUsername();
                String password = prefs.getPassword();

                List<Course> courses = ElearnClient.getInstance().fetchAllData(username, password, null);
                if (courses == null || courses.isEmpty()) return;

                // Save to cache
                JSONArray coursesArr = new JSONArray();
                List<ExerciseItem> allActiveExercises = new ArrayList<>();
                Set<String> currentExIds = new HashSet<>();
                Set<String> currentFileIds = new HashSet<>();

                List<ExerciseItem> newlyAddedExercises = new ArrayList<>();
                List<FileItem> newlyAddedFiles = new ArrayList<>();

                Set<String> knownExIds = prefs.getKnownExerciseIds();
                Set<String> knownFileIds = prefs.getKnownFileIds();
                boolean isFirstScan = knownExIds.isEmpty() && knownFileIds.isEmpty();

                for (Course c : courses) {
                    coursesArr.put(c.toJson());

                    for (ExerciseItem ex : c.getExercises()) {
                        currentExIds.add(ex.getExid());
                        if (!ex.isExpired()) {
                            allActiveExercises.add(ex);
                        }
                        if (!isFirstScan && !knownExIds.contains(ex.getExid())) {
                            newlyAddedExercises.add(ex);
                        }
                    }

                    for (FileItem f : c.getLearningFiles()) {
                        currentFileIds.add(f.getLid());
                        if (!isFirstScan && !knownFileIds.contains(f.getLid())) {
                            newlyAddedFiles.add(f);
                        }
                    }
                }

                prefs.setCachedData(coursesArr.toString());
                prefs.setLastSyncTime(System.currentTimeMillis());
                prefs.setKnownExerciseIds(currentExIds);
                prefs.setKnownFileIds(currentFileIds);

                // Re-schedule milestone reminders
                NotificationScheduler.scheduleExerciseReminders(context, allActiveExercises);

                // Notifications:
                // 1. Newly detected exercises
                for (ExerciseItem newEx : newlyAddedExercises) {
                    String title = "تمرین جدید در " + newEx.getCourseName();
                    String body = newEx.getTitle() + "\nمهلت تحویل: " + newEx.getPersianDeadline();
                    NotificationScheduler.showReminderNotification(context, title, body, newEx.getExid().hashCode());
                }

                // 2. Newly detected learning files
                for (FileItem newFile : newlyAddedFiles) {
                    String title = "فایل آموزشی جدید در " + newFile.getCourseName();
                    String body = newFile.getTitle();
                    NotificationScheduler.showSyncNotification(context, title, body, newFile.getLid().hashCode());
                }

                // 3. Scan reminder (if enabled)
                if (prefs.isNotifOnScan() && !allActiveExercises.isEmpty()) {
                    String title = "پایش دوره‌ای: " + PersianDateHelper.toPersianDigits(String.valueOf(allActiveExercises.size())) + " تمرین فعال دارید";
                    ExerciseItem nearest = allActiveExercises.get(0);
                    String body = "نزدیک‌ترین موعد: " + nearest.getCourseName() + " (" + nearest.getRemainingText() + ")";
                    NotificationScheduler.showReminderNotification(context, title, body, 9999);
                }

            } catch (Exception ignored) {}
        }).start();
    }
}
