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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.subway.Line;

/**
 * Created by gabriel on 6/27/17.
 */

public class LineStatusCache {
    private final Object lock = new Object();
    private HashMap<String, Status> lineStatuses = new HashMap<>();
    private Context context;

    public LineStatusCache(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class Status implements Serializable {
        private final String id;
        public final boolean down;
        public final boolean stopped;
        public final Date downSince;
        public final Date updated;
        public transient Line line;

        public Status(Line line) {
            this.id = line.getId();
            this.line = line;
            this.down = false;
            this.stopped = false;
            this.downSince = new Date();
            this.updated = new Date();
        }

        public Status(Line line, Date downSince) {
            this.id = line.getId();
            this.line = line;
            this.down = true;
            this.stopped = false;
            this.downSince = downSince;
            this.updated = new Date();
        }

        public Status(Line line, Date downSince, boolean stopped) {
            this.id = line.getId();
            this.line = line;
            this.down = true;
            this.stopped = stopped;
            this.downSince = downSince;
            this.updated = new Date();
        }
    }

    private static class StatusCache implements Serializable {
        private HashMap<String, Status> info;

        StatusCache(HashMap<String, Status> info) {
            this.info = info;
        }
    }

    private final String LINE_STATUS_CACHE_KEY = "LineStatus";

    private void cacheLineStatus(HashMap<String, Status> info) {
        CacheManager cm = Coordinator.get(context).getCacheManager();
        cm.put(LINE_STATUS_CACHE_KEY, new StatusCache(info));
    }

    private HashMap<String, Status> readLineStatus() {
        CacheManager cm = Coordinator.get(context).getCacheManager();
        StatusCache sc = cm.get(LINE_STATUS_CACHE_KEY, StatusCache.class);
        if (sc == null) {
            return null;
        }
        Map<String, Line> lines = new HashMap<>();
        for (Line l : Coordinator.get(context).getMapManager().getAllLines()) {
            lines.put(l.getId(), l);
        }
        for (Status s : sc.info.values()) {
            if (s == null) {
                return null;
            }
            s.line = lines.get(s.id);
        }
        return sc.info;
    }

    public Map<String, Status> getLineStatus() {
        synchronized (lock) {
            if (lineStatuses.isEmpty()) {
                lineStatuses = readLineStatus();
                if (lineStatuses == null) {
                    lineStatuses = new HashMap<>();
                }
            }
            return new HashMap<>(lineStatuses);
        }
    }

    public Status getLineStatus(String id) {
        synchronized (lock) {
            if (lineStatuses.isEmpty()) {
                lineStatuses = readLineStatus();
                if (lineStatuses == null) {
                    lineStatuses = new HashMap<>();
                }
            }
            return lineStatuses.get(id);
        }
    }

    public boolean isDisturbanceOngoing() {
        synchronized (lock) {
            Map<String, Status> statuses = getLineStatus();
            for (Status status : statuses.values()) {
                if (status.down) {
                    return true;
                }
            }
            return false;
        }
    }

    public void clear() {
        synchronized (lock) {
            lineStatuses.clear();
        }
    }

    public void updateLineStatus() {
        new UpdateLineStatusTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void markLineAsDown(Line line, Date since) {
        synchronized (lock) {
            // ensure line statuses are loaded, otherwise our map will only contain this line
            getLineStatus();

            lineStatuses.put(line.getId(), new Status(line, since));
            cacheLineStatus(lineStatuses);
        }
        Intent intent = new Intent(ACTION_LINE_STATUS_UPDATE_SUCCESS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    public void markLineAsUp(Line line) {
        synchronized (lock) {
            // ensure line statuses are loaded, otherwise our map will only contain this line
            getLineStatus();

            lineStatuses.put(line.getId(), new Status(line));
            cacheLineStatus(lineStatuses);
        }
        Intent intent = new Intent(ACTION_LINE_STATUS_UPDATE_SUCCESS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    private class UpdateLineStatusTask extends AsyncTask<Void, Integer, Boolean> {
        private HashMap<String, LineStatusCache.Status> statuses = new HashMap<>();

        @Override
        protected void onPreExecute() {
            Intent intent = new Intent(ACTION_LINE_STATUS_UPDATE_STARTED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(intent);
        }

        protected Boolean doInBackground(Void... v) {
            if (!Connectivity.isConnected(context)) {
                return false;
            }
            List<Line> lines = new LinkedList<>(Coordinator.get(context).getMapManager().getAllLines());
            if (lines.size() == 0) {
                return false;
            }
            try {
                List<API.Disturbance> disturbances = API.getInstance().getOngoingDisturbances();
                for (Line l : lines) {
                    boolean foundDisturbance = false;
                    for (API.Disturbance d : disturbances) {
                        if (d.line.equals(l.getId()) && !d.ended) {
                            foundDisturbance = true;
                            LineStatusCache.Status status;
                            Collections.sort(d.statuses, (o1, o2) -> {
                                long lhs = o1.time[0];
                                long rhs = o2.time[0];
                                return Long.compare(lhs, rhs);
                            });

                            boolean stopped = false;
                            if (d.statuses.size() > 0) {
                                API.Status lastStatus = d.statuses.get(d.statuses.size() - 1);
                                stopped = lastStatus.msgType.contains("_SINCE_") || lastStatus.msgType.contains("_HALTED_") || lastStatus.msgType.contains("_BETWEEN_");
                            }
                            status = new LineStatusCache.Status(l, new Date(d.startTime[0] * 1000), stopped);
                            statuses.put(l.getId(), status);
                            break;
                        }
                    }
                    if (!foundDisturbance) {
                        statuses.put(l.getId(), new LineStatusCache.Status(l));
                    }
                }
            } catch (APIException e) {
                return false;
            }
            cacheLineStatus(statuses);
            synchronized (lock) {
                lineStatuses.clear();
                lineStatuses.putAll(statuses);
            }
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            Intent intent;
            if (result) {
                intent = new Intent(ACTION_LINE_STATUS_UPDATE_SUCCESS);
            } else {
                intent = new Intent(ACTION_LINE_STATUS_UPDATE_FAILED);
            }
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(intent);
        }
    }

    public static final String ACTION_LINE_STATUS_UPDATE_STARTED = "im.tny.segvault.disturbances.action.linestatus.update.started";
    public static final String ACTION_LINE_STATUS_UPDATE_SUCCESS = "im.tny.segvault.disturbances.action.linestatus.update.success";
    public static final String ACTION_LINE_STATUS_UPDATE_FAILED = "im.tny.segvault.disturbances.action.linestatus.update.failure";
}
