package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 6/27/17.
 */

public class StatsCache {
    private final Object lock = new Object();
    private HashMap<String, Stats> networkStats = new HashMap<>();
    private Context context;
    private MainService mainService;

    public StatsCache(MainService mainService) {
        this.context = mainService;
        this.mainService = mainService;
    }

    public static class Stats implements Serializable {
        public HashMap<String, LineStats> weekLineStats = new HashMap<>();
        public HashMap<String, LineStats> monthLineStats = new HashMap<>();
        public Date lastDisturbance;
        public Date updated;
    }

    public static class LineStats implements Serializable {
        public double availability;
        public long averageDisturbanceDuration;
    }

    private final String STATS_CACHE_FILENAME = "StatsCache";

    private void cacheStats(HashMap<String, Stats> info) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), STATS_CACHE_FILENAME));
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(info);
            os.close();
            fos.close();
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            e.printStackTrace();
        }
    }

    private HashMap<String, Stats> readStats() {
        HashMap<String, Stats> info;
        try {
            FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), STATS_CACHE_FILENAME));
            ObjectInputStream is = new ObjectInputStream(fis);
            info = (HashMap) is.readObject();
            is.close();
            fis.close();

            Map<String, Line> lines = new HashMap<>();
            for (Line l : mainService.getAllLines()) {
                lines.put(l.getId(), l);
            }
            for (Stats stats : info.values()) {
                if (!(stats instanceof Stats)) {
                    throw new Exception();
                }
                for (LineStats l : stats.weekLineStats.values()) {
                    if (!(l instanceof LineStats)) {
                        throw new Exception();
                    }
                }
                for (LineStats l : stats.monthLineStats.values()) {
                    if (!(l instanceof LineStats)) {
                        throw new Exception();
                    }
                }
            }
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            return null;
        }
        return info;
    }

    public HashMap<String, Stats> getStats() {
        HashMap<String, Stats> tstats = new HashMap<>();
        synchronized (lock) {
            if (networkStats.isEmpty()) {
                networkStats = readStats();
                if (networkStats == null) {
                    networkStats = new HashMap<>();
                }
            }
            tstats.putAll(networkStats);
        }
        return tstats;
    }

    public Stats getNetworkStats(String id) {
        return getStats().get(id);
    }

    public void clear() {
        synchronized (lock) {
            networkStats.clear();
        }
    }

    public void updateStats() {
        new UpdateStatsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class UpdateStatsTask extends AsyncTask<Void, Integer, Boolean> {
        private HashMap<String, Stats> stats = new HashMap<>();

        @Override
        protected void onPreExecute() {
            Intent intent = new Intent(ACTION_STATS_UPDATE_STARTED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(intent);
        }

        protected Boolean doInBackground(Void... v) {
            if (!Connectivity.isConnected(context)) {
                return false;
            }
            API api = API.getInstance();
            Date now = new Date();
            Date weekAgo = new Date(now.getTime() - TimeUnit.DAYS.toMillis(7));
            Date monthAgo = new Date(now.getTime() - TimeUnit.DAYS.toMillis(30));

            for (Network n : mainService.getNetworks()) {
                try {
                    Stats netStats = new Stats();

                    API.Stats apiStats = api.getStats(n.getId(), monthAgo, now);
                    for(Map.Entry<String, API.LineStats> apiLineStats : apiStats.lineStats.entrySet()) {
                        LineStats lineStats = new LineStats();
                        lineStats.availability = apiLineStats.getValue().availability;
                        lineStats.averageDisturbanceDuration = apiLineStats.getValue().avgDistDuration * 1000;
                        netStats.monthLineStats.put(apiLineStats.getKey(), lineStats);
                    }

                    netStats.lastDisturbance = new Date(apiStats.lastDisturbance[0] * 1000);

                    apiStats = api.getStats(n.getId(), weekAgo, now);
                    for(Map.Entry<String, API.LineStats> apiLineStats : apiStats.lineStats.entrySet()) {
                        LineStats lineStats = new LineStats();
                        lineStats.availability = apiLineStats.getValue().availability;
                        lineStats.averageDisturbanceDuration = apiLineStats.getValue().avgDistDuration * 1000;
                        netStats.weekLineStats.put(apiLineStats.getKey(), lineStats);
                    }

                    netStats.updated = now;

                    stats.put(n.getId(), netStats);
                } catch (APIException e) {
                    return false;
                }
            }
            cacheStats(stats);
            synchronized (lock) {
                networkStats.clear();
                networkStats.putAll(stats);
            }
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            Intent intent;
            if (result) {
                intent = new Intent(ACTION_STATS_UPDATE_SUCCESS);
            } else {
                intent = new Intent(ACTION_STATS_UPDATE_FAILED);
            }
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(intent);
        }
    }

    public static final String ACTION_STATS_UPDATE_STARTED = "im.tny.segvault.disturbances.action.networkStats.update.started";
    public static final String ACTION_STATS_UPDATE_SUCCESS = "im.tny.segvault.disturbances.action.networkStats.update.success";
    public static final String ACTION_STATS_UPDATE_FAILED = "im.tny.segvault.disturbances.action.networkStats.update.failure";
}
