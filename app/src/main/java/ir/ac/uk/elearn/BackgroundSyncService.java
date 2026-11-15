package ir.ac.uk.elearn;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackgroundSyncService extends Service {

    public static final String ACTION_START_SYNC = "ir.ac.uk.elearn.ACTION_START_SYNC";
    public static final String ACTION_DATA_UPDATED = "ir.ac.uk.elearn.ACTION_DATA_UPDATED";

    public static final int NOTIF_ID_SYNC_PROGRESS = 7001;
    public static final int NOTIF_ID_SYNC_RESULT = 7002;

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elearn:sync_wakelock");
                wakeLock.acquire(90000L); // 90 seconds max safety timeout
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Immediately start foreground so system never kills this process during network operations
        Notification progressNotif = NotificationScheduler.buildSyncProgressNotification(
                this,
                "در حال پایش و بررسی تمرین‌های جدید سامانه‌ی elearn...",
                0,
                100
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID_SYNC_PROGRESS, progressNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID_SYNC_PROGRESS, progressNotif);
            }
        } catch (Throwable t) {
            try {
                startForeground(NOTIF_ID_SYNC_PROGRESS, progressNotif);
            } catch (Throwable ignored) {}
        }

        // 2. Perform background sync
        new Thread(() -> {
            try {
                performSync();
            } finally {
                stopForeground(true);
                releaseWakeLock();
                stopSelf();
                // Ensure the next scheduled alarm is armed
                NotificationScheduler.schedulePeriodicSync(getApplicationContext());
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void performSync() {
        AppPreferences prefs = AppPreferences.getInstance(this);
        if (!prefs.isLoggedIn() || !prefs.isSyncEnabled()) {
            return;
        }

        String username = prefs.getUsername();
        String password = prefs.getPassword();
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            List<Course> courses = ElearnClient.getInstance().fetchAllData(username, password, (msg, current, total) -> {
                try {
                    if (nm != null) {
                        Notification notif = NotificationScheduler.buildSyncProgressNotification(this, msg, current, total);
                        nm.notify(NOTIF_ID_SYNC_PROGRESS, notif);
                    }
                } catch (Throwable ignored) {}
            });

            if (courses == null || courses.isEmpty()) {
                return;
            }

            // Cache data
            JSONArray coursesArr = new JSONArray();
            List<ExerciseItem> allActiveExercises = new ArrayList<>();
            Set<String> currentExIds = new HashSet<>();
            Set<String> currentFileIds = new HashSet<>();

            List<ExerciseItem> newlyAddedExercises = new ArrayList<>();
            List<FileItem> newlyAddedFiles = new ArrayList<>();
            Set<String> newUnseenExIds = new HashSet<>();

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
                        newUnseenExIds.add(ex.getExid());
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

            // Re-arm milestone alarms for active exercises
            NotificationScheduler.scheduleExerciseReminders(this, allActiveExercises);

            // 1. Notify newly added exercises
            if (!newUnseenExIds.isEmpty()) {
                prefs.addUnseenNewExerciseIds(newUnseenExIds);

                for (ExerciseItem newEx : newlyAddedExercises) {
                    String title = "🔔 تمرین جدید در " + newEx.getCourseName();
                    String body = newEx.getTitle() + "\nمهلت تحویل: " + newEx.getPersianDeadline();
                    NotificationScheduler.showNewExerciseNotification(this, title, body, newEx.getExid().hashCode());
                }
            }

            // 2. Notify newly added educational files
            for (FileItem newFile : newlyAddedFiles) {
                String title = "فایل آموزشی جدید در " + newFile.getCourseName();
                String body = newFile.getTitle();
                NotificationScheduler.showSyncNotification(this, title, body, newFile.getLid().hashCode());
            }

            // 3. Notify the scan result in notification center
            String resultTitle = "پایش خودکار سامانه elearn انجام شد";
            String resultBody;
            int activeCount = allActiveExercises.size();
            if (activeCount == 0) {
                resultBody = "در حال حاضر هیچ تمرین فعالی در سامانه وجود ندارد.";
            } else if (activeCount == 1) {
                ExerciseItem single = allActiveExercises.get(0);
                resultBody = "۱ تمرین فعال یافت شد (" + single.getCourseName() + " - " + single.getRemainingText() + ")";
            } else {
                ExerciseItem first = allActiveExercises.get(0);
                resultBody = PersianDateHelper.toPersianDigits(String.valueOf(activeCount)) + " تمرین فعال با مهلت تحویل یافت شد.\nنزدیک‌ترین: " + first.getCourseName() + " (" + first.getRemainingText() + ")";
            }
            NotificationScheduler.showSyncNotification(this, resultTitle, resultBody, NOTIF_ID_SYNC_RESULT);

            // 4. Send local broadcast so MainActivity can update UI live if open
            Intent updateIntent = new Intent(ACTION_DATA_UPDATED);
            updateIntent.putExtra("new_count", newUnseenExIds.size());
            sendBroadcast(updateIntent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
