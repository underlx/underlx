package im.tny.segvault.disturbances;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobRequest;

import java.util.concurrent.TimeUnit;

public class OurJobCreator implements JobCreator {
    @Override
    public Job create(String tag) {
        switch (tag) {
            case MapManager.CheckUpdatesJob.TAG:
            case MapManager.CheckUpdatesJob.NO_UPDATE_TAG:
            case MapManager.CheckUpdatesJob.AUTO_UPDATE_TAG:
                return new MapManager.CheckUpdatesJob(tag);
            case SyncTripsJob.TAG:
                return new SyncTripsJob();
            default:
                return null;
        }
    }

    public static void scheduleAllJobs() {
        MapManager.CheckUpdatesJob.schedule();
        SyncTripsJob.schedule();
    }

    public static class SyncTripsJob extends Job {
        public static final String TAG = "job_sync_trips";

        @Override
        @NonNull
        protected Result onRunJob(Params params) {
            Coordinator.get(getContext()).getSynchronizer().sync();
            return Result.SUCCESS;
        }

        public static void schedule() {
            schedule(true);
        }

        public static void schedule(boolean updateCurrent) {
            new JobRequest.Builder(SyncTripsJob.TAG)
                    .setExecutionWindow(TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(48))
                    .setBackoffCriteria(TimeUnit.HOURS.toMillis(1), JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setUpdateCurrent(updateCurrent)
                    .build()
                    .schedule();
        }
    }
}
