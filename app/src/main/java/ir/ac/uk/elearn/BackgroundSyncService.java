package ir.ac.uk.elearn;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;

public class BackgroundSyncService extends Service {

    public static final String ACTION_START_SYNC = "ir.ac.uk.elearn.ACTION_START_SYNC";
    public static final String ACTION_DATA_UPDATED = "ir.ac.uk.elearn.ACTION_DATA_UPDATED";

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elearn:sync_wakelock");
                wakeLock.acquire(90000L); // 90 seconds safety timeout
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. If network is disconnected: do NOT show any notification!
        if (!NetworkHelper.isNetworkAvailable(this)) {
            NetworkSyncManager.waitForNetworkAndSync(this);
            releaseWakeLock();
            stopSelf();
            return START_NOT_STICKY;
        }

        // 2. Start foreground service with progress notification
        Notification progressNotif = NotificationScheduler.buildSyncProgressNotification(
                this,
                "در حال پایش و بررسی تمرین‌های سامانه‌ی ایلرن!...",
                0,
                100
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationScheduler.NOTIF_ID_SYNC_PROGRESS, progressNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NotificationScheduler.NOTIF_ID_SYNC_PROGRESS, progressNotif);
            }
        } catch (Throwable t) {
            try {
                startForeground(NotificationScheduler.NOTIF_ID_SYNC_PROGRESS, progressNotif);
            } catch (Throwable ignored) {}
        }

        // 3. Perform sync in background thread
        new Thread(() -> {
            try {
                BackgroundSyncHelper.doSync(getApplicationContext());
            } finally {
                try {
                    stopForeground(true);
                } catch (Throwable ignored) {}
                NotificationScheduler.cancelSyncProgress(this);
                releaseWakeLock();
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
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
