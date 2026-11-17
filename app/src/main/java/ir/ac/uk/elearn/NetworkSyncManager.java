package ir.ac.uk.elearn;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

public class NetworkSyncManager {

    public static final int JOB_ID_NETWORK_SYNC = 9001;
    private static ConnectivityManager.NetworkCallback activeNetworkCallback = null;
    private static final Object CALLBACK_LOCK = new Object();

    public static void waitForNetworkAndSync(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        // 1. Schedule system-level JobScheduler (works even if process dies)
        scheduleJob(appContext);

        // 2. Register instant in-process NetworkCallback if supported
        registerInstantCallback(appContext);
    }

    private static void scheduleJob(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) {
                    ComponentName serviceComponent = new ComponentName(context, NetworkSyncJobService.class);
                    JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_NETWORK_SYNC, serviceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder.setRequiresBatteryNotLow(false);
                    }
                    try {
                        builder.setPersisted(true);
                    } catch (Throwable ignored) {}

                    js.schedule(builder.build());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void registerInstantCallback(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            synchronized (CALLBACK_LOCK) {
                if (activeNetworkCallback != null) return; // already registered

                try {
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        activeNetworkCallback = new ConnectivityManager.NetworkCallback() {
                            @Override
                            public void onAvailable(Network network) {
                                unregisterInstantCallback(context);
                                cancelJob(context);

                                // Network is back! Run sync in background thread
                                new Thread(() -> BackgroundSyncHelper.doSync(context)).start();
                            }
                        };
                        NetworkRequest request = new NetworkRequest.Builder()
                                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .build();
                        cm.registerNetworkCallback(request, activeNetworkCallback);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public static void unregisterInstantCallback(Context context) {
        synchronized (CALLBACK_LOCK) {
            if (activeNetworkCallback != null) {
                try {
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        cm.unregisterNetworkCallback(activeNetworkCallback);
                    }
                } catch (Throwable ignored) {}
                activeNetworkCallback = null;
            }
        }
    }

    public static void cancelJob(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) {
                    js.cancel(JOB_ID_NETWORK_SYNC);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void cancelAll(Context context) {
        unregisterInstantCallback(context);
        cancelJob(context);
    }
}
