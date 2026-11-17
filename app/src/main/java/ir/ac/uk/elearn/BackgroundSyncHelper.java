package ir.ac.uk.elearn;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackgroundSyncHelper {

    private static final Object SYNC_LOCK = new Object();
    private static volatile boolean isSyncRunning = false;

    public static boolean isSyncRunning() {
        return isSyncRunning;
    }

    public static void doSync(Context context) {
        if (context == null) return;
        AppPreferences prefs = AppPreferences.getInstance(context);
        if (!prefs.isLoggedIn() || !prefs.isSyncEnabled()) {
            return;
        }

        // 1. Check network: if no net, wait silently without notification!
        if (!NetworkHelper.isNetworkAvailable(context)) {
            NetworkSyncManager.waitForNetworkAndSync(context);
            return;
        }

        synchronized (SYNC_LOCK) {
            if (isSyncRunning) return;
            isSyncRunning = true;
        }

        try {
            String username = prefs.getUsername();
            String password = prefs.getPassword();
            if (username.isEmpty() || password.isEmpty()) {
                return;
            }

            // 2. Show live progress notification
            NotificationScheduler.showSyncProgress(context, "در حال پایش و بروزرسانی دروس...", 0, 100);

            List<Course> courses = ElearnClient.getInstance().fetchAllData(username, password, (msg, current, total) -> {
                NotificationScheduler.showSyncProgress(context, msg, current, total);
            });

            if (courses == null || courses.isEmpty()) {
                NotificationScheduler.cancelSyncProgress(context);
                return;
            }

            // 3. Process & cache data
            JSONArray coursesArr = new JSONArray();
            List<ExerciseItem> allActiveExercises = new ArrayList<>();
            Set<String> currentExIds = new HashSet<>();
            Set<String> currentFileIds = new HashSet<>();

            List<ExerciseItem> newlyAddedExercises = new ArrayList<>();
            List<FileItem> newlyAddedFiles = new ArrayList<>();
            List<MessageItem> newlyAddedMessages = new ArrayList<>();
            Set<String> newUnseenExIds = new HashSet<>();

            Set<String> knownExIds = prefs.getKnownExerciseIds();
            Set<String> knownFileIds = prefs.getKnownFileIds();
            Set<String> knownMsgIds = prefs.getKnownMessageIds();
            Set<String> currentMsgIds = new HashSet<>();
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
                        newUnseenExIds.add(ex.getExid());
                    }
                }

                for (FileItem f : c.getLearningFiles()) {
                    currentFileIds.add(f.getLid());
                    if (!isFirstScan && !knownFileIds.contains(f.getLid())) {
                        newlyAddedFiles.add(f);
                    }
                }

                for (MessageItem m : c.getMessages()) {
                    currentMsgIds.add(m.getId());
                    if (!isFirstScan && !knownMsgIds.contains(m.getId())) {
                        newlyAddedMessages.add(m);
                    }
                }
            }

            prefs.setCachedData(coursesArr.toString());
            prefs.setLastSyncTime(System.currentTimeMillis());
            prefs.setKnownExerciseIds(currentExIds);
            prefs.setKnownFileIds(currentFileIds);
            prefs.setKnownMessageIds(currentMsgIds);

            // Re-arm milestone alarms for active exercises
            NotificationScheduler.scheduleExerciseReminders(context, allActiveExercises);

            // 4. Cancel progress notification so it goes away
            NotificationScheduler.cancelSyncProgress(context);

            // 5. Notify newly added exercises
            if (!newUnseenExIds.isEmpty()) {
                prefs.addUnseenNewExerciseIds(newUnseenExIds);

                for (ExerciseItem newEx : newlyAddedExercises) {
                    String title = "🔔 تمرین جدید در " + newEx.getCourseName();
                    String body = newEx.getTitle() + "\nمهلت تحویل: " + newEx.getPersianDeadline();
                    NotificationScheduler.showNewExerciseNotification(context, title, body, newEx.getExid().hashCode());
                }
            }

            // 6. Notify newly added educational files
            for (FileItem newFile : newlyAddedFiles) {
                String title = "فایل آموزشی جدید در " + newFile.getCourseName();
                String body = newFile.getTitle();
                NotificationScheduler.showSyncNotification(context, title, body, newFile.getLid().hashCode());
            }

            // 7. Notify newly added professor messages / announcements
            for (MessageItem newMsg : newlyAddedMessages) {
                String title = "📩 پیام جدید از " + newMsg.getSenderName() + " (" + newMsg.getCourseName() + ")";
                String body = newMsg.getTitle() + (newMsg.getMessage().isEmpty() ? "" : "\n" + newMsg.getMessage());
                NotificationScheduler.showSyncNotification(context, title, body, newMsg.getId().hashCode());
            }

            // 8. Notify scan result
            String resultTitle = "پایش سامانه ایلرن! انجام شد";
            String resultBody;
            int activeCount = allActiveExercises.size();
            if (activeCount == 0) {
                resultBody = "در حال حاضر هیچ تمرین فعالی در سامانه وجود ندارد.";
            } else if (activeCount == 1) {
                ExerciseItem single = allActiveExercises.get(0);
                resultBody = "۱ تمرین فعال با مهلت تحویل یافت شد (" + single.getCourseName() + " - " + single.getRemainingText() + ")";
            } else {
                ExerciseItem first = allActiveExercises.get(0);
                resultBody = PersianDateHelper.toPersianDigits(String.valueOf(activeCount)) + " تمرین فعال با مهلت تحویل یافت شد.\nنزدیک‌ترین: " + first.getCourseName() + " (" + first.getRemainingText() + ")";
            }
            NotificationScheduler.showSyncResultNotification(context, resultTitle, resultBody);

            // 8. Broadcast update to MainActivity
            Intent updateIntent = new Intent(BackgroundSyncService.ACTION_DATA_UPDATED);
            updateIntent.putExtra("new_count", newUnseenExIds.size());
            context.sendBroadcast(updateIntent);

        } catch (Exception e) {
            e.printStackTrace();
            NotificationScheduler.cancelSyncProgress(context);

            if (!NetworkHelper.isNetworkAvailable(context) || NetworkHelper.isNetworkException(e)) {
                // Disconnected mid-sync: wait silently without any notification!
                NetworkSyncManager.waitForNetworkAndSync(context);
            } else {
                NotificationScheduler.showSyncResultNotification(context, "خطا در پایش سامانه ایلرن!", "ارتباط با سامانه برقرار نشد: " + e.getMessage());
            }
        } finally {
            synchronized (SYNC_LOCK) {
                isSyncRunning = false;
            }
            // Ensure next periodic sync is scheduled
            NotificationScheduler.schedulePeriodicSync(context.getApplicationContext());
        }
    }
}
