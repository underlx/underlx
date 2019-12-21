package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.Feedback;
import im.tny.segvault.disturbances.database.StationUse;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.exception.APIException;

/**
 * Created by Gabriel on 26/07/2017.
 */

public class Synchronizer {
    private Context context;

    private LocalBroadcastManager bm;
    private Date lastSync;
    private Object lock = new Object();

    public Synchronizer(Context applicationContext) {
        context = applicationContext;

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainService.ACTION_TRIP_TABLE_UPDATED);
        filter.addAction(MainService.ACTION_FEEDBACK_TABLE_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }

    public void asyncSync() {
        new SyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void sync() {
        Log.d("sync", "Waiting for lock");
        synchronized (lock) {
            Log.d("sync", "Lock acquired");
            if (!Connectivity.isConnected(context)) {
                Log.d("sync", "Not connected, abort");
                return;
            }
            if(lastSync != null && new Date().getTime() - lastSync.getTime() < TimeUnit.SECONDS.toMillis(2)) {
                Log.d("sync", "Last sync was right now, abort");
                // avoid looping on ACTION_TRIP_TABLE_UPDATED broadcasts
                return;
            }

            AppDatabase db = Coordinator.get(context).getDB();
            db.runInTransaction(() -> {
                for(Trip t : db.tripDao().getUnsynced()) {
                    API.TripRequest request = tripToAPIRequest(db, t);
                    try {
                        if (t.submitted) {
                            // submit update, if possible
                            if (t.canBeCorrected(db)) {
                                API.getInstance().putTrip(request);
                                t.synced = true;
                                t.submitted = true;
                            }
                        } else {
                            // submit new
                            API.getInstance().postTrip(request);
                            t.synced = true;
                            t.submitted = true;
                        }
                    } catch (APIException e) {
                        t.syncFailures++;
                        if(t.syncFailures >= 5) {
                            // give up on this one
                            t.synced = true;
                        }
                    }
                    db.tripDao().updateAll(t);
                }

                for (Feedback f : db.feedbackDao().getUnsynced()) {
                    API.Feedback request = feedbackToAPIRequest(f);
                    try {
                        API.getInstance().postFeedback(request);
                        f.synced = true;
                        db.feedbackDao().updateAll(f);
                    } catch (APIException e) {
                        e.printStackTrace();
                    }
                }
            });
            lastSync = new Date();
            Log.d("sync", "Sync done");
        }
    }

    private API.TripRequest tripToAPIRequest(AppDatabase db, Trip t) {
        API.TripRequest request = new API.TripRequest();
        request.id = t.id;
        request.userConfirmed = t.userConfirmed;
        List<StationUse> path = db.stationUseDao().getOfTrip(t.id);
        request.uses = new ArrayList<>(path.size());
        for (StationUse use : path) {
            request.uses.add(stationUseToAPI(use));
        }
        return request;
    }

    private API.StationUse stationUseToAPI(StationUse use) {
        API.StationUse apiUse = new API.StationUse();
        apiUse.station = use.stationID;
        apiUse.entryTime = new long[]{use.entryDate.getTime() / 1000, (use.entryDate.getTime() % 1000) * 1000000};
        apiUse.leaveTime = new long[]{use.leaveDate.getTime() / 1000, (use.leaveDate.getTime() % 1000) * 1000000};
        apiUse.type = use.type.name();
        apiUse.manual = use.manualEntry;
        if (use.type == StationUse.UseType.INTERCHANGE) {
            apiUse.sourceLine = use.sourceLine;
            apiUse.targetLine = use.targetLine;
        }
        return apiUse;
    }

    private API.Feedback feedbackToAPIRequest(Feedback f) {
        API.Feedback request = new API.Feedback();
        request.id = f.id;
        request.timestamp = new long[]{f.timestamp.getTime() / 1000, (f.timestamp.getTime() % 1000) * 1000000};
        request.type = f.type;
        request.contents = f.contents;
        return request;
    }

    private class SyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            sync();
            return null;
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainService.ACTION_TRIP_TABLE_UPDATED:
                case MainService.ACTION_FEEDBACK_TABLE_UPDATED:
                    // TODO. this may cause cycles (we change the database ourselves)
                    new SyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
            }
        }
    };
}
