package ir.ac.uk.elearn;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class NetworkSyncJobService extends JobService {

    private Thread syncThread;

    @Override
    public boolean onStartJob(JobParameters params) {
        syncThread = new Thread(() -> {
            try {
                BackgroundSyncHelper.doSync(getApplicationContext());
            } finally {
                jobFinished(params, false);
            }
        });
        syncThread.start();
        return true; // True because work is being performed asynchronously on syncThread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (syncThread != null && syncThread.isAlive()) {
            syncThread.interrupt();
        }
        // Reschedule if stopped prematurely
        return true;
    }
}
