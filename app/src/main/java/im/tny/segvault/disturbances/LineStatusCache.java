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

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.subway.Line;

/**
 * Created by gabriel on 6/27/17.
 */

public class LineStatusCache {
    private final Object lock = new Object();
    private HashMap<String, Status> lineStatuses = new HashMap<>();
    private Context context;
    private MainService mainService;

    public LineStatusCache(MainService mainService) {
        this.context = mainService;
        this.mainService = mainService;
    }

    public static class Status implements Serializable {
        private final String id;
        public final boolean down;
        public final Date downSince;
        public final Date updated;
        public transient Line line;

        public Status(Line line) {
            this.id = line.getId();
            this.line = line;
            this.down = false;
            this.downSince = new Date();
            this.updated = new Date();
        }

        public Status(Line line, Date downSince) {
            this.id = line.getId();
            this.line = line;
            this.down = true;
            this.downSince = downSince;
            this.updated = new Date();
        }
    }

    private final String LINE_STATUS_CACHE_FILENAME = "LineStatusCache";

    private void cacheLineStatus(HashMap<String, Status> info) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), LINE_STATUS_CACHE_FILENAME));
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

    private HashMap<String, Status> readLineStatus() {
        HashMap<String, Status> info;
        try {
            FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), LINE_STATUS_CACHE_FILENAME));
            ObjectInputStream is = new ObjectInputStream(fis);
            info = (HashMap) is.readObject();
            is.close();
            fis.close();

            Map<String, Line> lines = new HashMap<>();
            for (Line l : mainService.getAllLines()) {
                lines.put(l.getId(), l);
            }
            for (Status s : info.values()) {
                if(!(s instanceof Status)) {
                    throw new Exception();
                }
                s.line = lines.get(s.id);
            }
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            return null;
        }
        return info;
    }

    public Map<String, Status> getLineStatus() {
        HashMap<String, Status> statuses = new HashMap<>();
        synchronized (lock) {
            if (lineStatuses.isEmpty()) {
                lineStatuses = readLineStatus();
                if (lineStatuses == null) {
                    lineStatuses = new HashMap<>();
                }
            }
            statuses.putAll(lineStatuses);
        }
        return statuses;
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
            lineStatuses.put(line.getId(), new Status(line, since));
            cacheLineStatus(lineStatuses);
        }
        Intent intent = new Intent(ACTION_LINE_STATUS_UPDATE_SUCCESS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    public void markLineAsUp(Line line) {
        synchronized (lock) {
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
            List<Line> lines = new LinkedList<>(mainService.getAllLines());
            try {
                List<API.Disturbance> disturbances = API.getInstance().getOngoingDisturbances();
                for (Line l : lines) {
                    boolean foundDisturbance = false;
                    for (API.Disturbance d : disturbances) {
                        if (d.line.equals(l.getId()) && !d.ended) {
                            foundDisturbance = true;
                            statuses.put(l.getId(), new LineStatusCache.Status(l, new Date(d.startTime[0] * 1000)));
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
