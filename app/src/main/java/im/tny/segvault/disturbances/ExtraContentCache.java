package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 7/15/17.
 */

public class ExtraContentCache {
    public static void getTrivia(Context context, OnTriviaReadyListener listener, Station... stations) {
        new RetrieveTriviaTask(context, listener).execute(stations);
    }

    public static void getConnectionInfo(Context context, OnConnectionInfoReadyListener listener, String type, Station... stations) {
        new RetrieveConnectionInfoTask(context, listener, type).execute(stations);
    }

    public static void cacheAllExtras(final Context context, final OnCacheAllListener listener, Network network) {
        String[] types = new String[5];
        types[0] = Station.CONNECTION_TYPE_BOAT;
        types[1] = Station.CONNECTION_TYPE_BUS;
        types[2] = Station.CONNECTION_TYPE_TRAIN;
        types[3] = Station.CONNECTION_TYPE_PARK;
        types[4] = Station.CONNECTION_TYPE_BIKE;
        Locale l = Util.getCurrentLocale(context);
        int total = 0;
        Collection<Station> stations = network.getStations();
        for (Station station : stations) {
            for (String type : types) {
                String lang = l.getLanguage();
                String url = station.getConnectionURLforLocale(type, lang);
                if (url == null) {
                    lang = "en";
                    url = station.getConnectionURLforLocale(type, lang);
                    if (url != null) {
                        total++;
                    }
                } else {
                    total++;
                }
            }
            total++; // for trivia
        }
        final int finalTotal = total;
        listener.onProgress(0, finalTotal);

        final Station[] stationsArray = stations.toArray(new Station[stations.size()]);

        new RetrieveConnectionInfoTask(context, new OnConnectionInfoReadyListener() {
            private int lastCurrent = 0;

            @Override
            public void onSuccess(List<String> connectionInfo) {
                new RetrieveTriviaTask(context, new OnTriviaReadyListener() {
                    @Override
                    public void onSuccess(List<String> trivia) {
                        listener.onSuccess();
                    }

                    @Override
                    public void onProgress(int current) {
                        listener.onProgress(lastCurrent + current, finalTotal);
                    }

                    @Override
                    public void onFailure() {
                        listener.onFailure();
                    }
                }).execute(stationsArray);
            }

            @Override
            public void onProgress(int current) {
                listener.onProgress(current, finalTotal);
                lastCurrent = current;
            }

            @Override
            public void onFailure() {
                listener.onFailure();
            }
        }, types).execute(stationsArray);
    }

    public static void clearAllCachedExtras(Context context) {
        File cacheDir = context.getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("ConnCache-") || file.getName().startsWith("TriviaCache-")) {
                    file.delete();
                }
            }
        }
    }

    private static class RetrieveTriviaTask extends AsyncTask<Station, Integer, List<String>> {
        private Context context;
        private OnTriviaReadyListener listener;
        private int count;

        public RetrieveTriviaTask(Context context, OnTriviaReadyListener listener) {
            this.context = context;
            this.listener = listener;
        }

        @Override
        protected List<String> doInBackground(Station... arrStation) {
            List<String> retList = new ArrayList<>();
            Locale l = Util.getCurrentLocale(context);
            count = arrStation.length;
            int current = 0;
            for (Station station : arrStation) {
                publishProgress(current++);
                String lang = l.getLanguage();
                String url = station.getTriviaURLforLocale(lang);
                if (url == null) {
                    lang = "en";
                    url = station.getTriviaURLforLocale(lang);
                    if (url == null) {
                        continue;
                    }
                }
                String response = retrieveTrivia(7, station.getId(), lang);
                if (response != null) {
                    retList.add(response);
                    continue;
                }
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                    h.setRequestProperty("Accept-Encoding", "gzip");
                    h.setRequestMethod("GET");
                    h.setDoInput(true);

                    InputStream is;
                    int code;
                    try {
                        // Will throw IOException if server responds with 401.
                        code = h.getResponseCode();
                    } catch (IOException e) {
                        // Will return 401, because now connection has the correct internal state.
                        code = h.getResponseCode();
                    }
                    if (code == 200) {
                        is = h.getInputStream();
                    } else {
                        continue;
                    }

                    if ("gzip".equals(h.getContentEncoding())) {
                        is = new GZIPInputStream(is);
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8);
                    StringBuilder sb = new StringBuilder();
                    String line = null;
                    while ((line = reader.readLine()) != null)
                        sb.append(line + "\n");

                    response = sb.toString();
                    cacheTrivia(response, station.getId(), lang);
                    retList.add(response);
                } catch (IOException e) {
                    continue;
                }
            }
            return retList;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            listener.onProgress(values[0]);
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if (result == null || result.size() != count) {
                listener.onFailure();
            } else {
                listener.onSuccess(result);
            }
        }

        // cache mechanism
        private final String TRIVIA_CACHE_FILENAME = "TriviaCache-%s-%s";

        private void cacheTrivia(String trivia, String stationId, String locale) {
            CachedTrivia toCache = new CachedTrivia(trivia);
            try {
                FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), String.format(TRIVIA_CACHE_FILENAME, stationId, locale)));
                ObjectOutputStream os = new ObjectOutputStream(fos);
                os.writeObject(toCache);
                os.close();
                fos.close();
            } catch (Exception e) {
                // oh well, we'll have to do without cache
                // caching is best-effort
                e.printStackTrace();
            }
        }

        @Nullable
        private String retrieveTrivia(int maxAgeDays, String stationId, String locale) {
            try {
                FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), String.format(TRIVIA_CACHE_FILENAME, stationId, locale)));
                ObjectInputStream is = new ObjectInputStream(fis);
                CachedTrivia cached = (CachedTrivia) is.readObject();
                is.close();
                fis.close();

                if (cached.date.getTime() < new Date().getTime() - 1000 * 60 * 60 * 24 * maxAgeDays && Connectivity.isConnected(context)) {
                    return null;
                }
                return cached.html;
            } catch (Exception e) {
                // oh well, we'll have to do without cache
                // caching is best-effort
                return null;
            }
        }
    }

    private static class CachedTrivia implements Serializable {
        public String html;
        public Date date;

        public CachedTrivia(String html) {
            this.html = html;
            this.date = new Date();
        }
    }

    public interface OnTriviaReadyListener {
        void onSuccess(List<String> trivia);

        void onProgress(int current);

        void onFailure();
    }

    private static class RetrieveConnectionInfoTask extends AsyncTask<Station, Integer, List<String>> {
        private String[] types;
        private Context context;
        private OnConnectionInfoReadyListener listener;
        private int count = 0;

        RetrieveConnectionInfoTask(Context context, OnConnectionInfoReadyListener listener, String... types) {
            this.context = context;
            this.listener = listener;
            this.types = types;
        }

        @Override
        protected List<String> doInBackground(Station... arrStation) {
            List<String> retList = new ArrayList<>();
            Locale l = Util.getCurrentLocale(context);
            for (Station station : arrStation) {
                for (String type : types) {
                    String lang = l.getLanguage();
                    String url = station.getConnectionURLforLocale(type, lang);
                    if (url == null) {
                        lang = "en";
                        url = station.getConnectionURLforLocale(type, lang);
                        if (url == null) {
                            continue;
                        }
                    }
                    publishProgress(count++);
                    String response = retrieveConnectionInfo(7, station.getId(), type, lang);
                    if (response != null) {
                        retList.add(response);
                        continue;
                    }
                    try {
                        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                        h.setRequestProperty("Accept-Encoding", "gzip");
                        h.setRequestMethod("GET");
                        h.setDoInput(true);

                        InputStream is;
                        int code;
                        try {
                            // Will throw IOException if server responds with 401.
                            code = h.getResponseCode();
                        } catch (IOException e) {
                            // Will return 401, because now connection has the correct internal state.
                            code = h.getResponseCode();
                        }
                        if (code == 200) {
                            is = h.getInputStream();
                        } else {
                            continue;
                        }

                        if ("gzip".equals(h.getContentEncoding())) {
                            is = new GZIPInputStream(is);
                        }

                        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8);
                        StringBuilder sb = new StringBuilder();
                        String line = null;
                        while ((line = reader.readLine()) != null)
                            sb.append(line + "\n");

                        response = sb.toString();
                        cacheConnectionInfo(response, station.getId(), type, lang);
                        retList.add(response);
                    } catch (IOException e) {
                        continue;
                    }
                }
            }
            return retList;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            listener.onProgress(values[0]);
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if (result == null || result.size() != count) {
                listener.onFailure();
            } else {
                listener.onSuccess(result);
            }
        }

        // cache mechanism
        private final String CONN_INFO_CACHE_FILENAME = "ConnCache-%s-%s-%s";

        private void cacheConnectionInfo(String trivia, String stationId, String type, String locale) {
            CachedConnectionInfo toCache = new CachedConnectionInfo(trivia);
            try {
                FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), String.format(CONN_INFO_CACHE_FILENAME, stationId, type, locale)));
                ObjectOutputStream os = new ObjectOutputStream(fos);
                os.writeObject(toCache);
                os.close();
                fos.close();
            } catch (Exception e) {
                // oh well, we'll have to do without cache
                // caching is best-effort
                e.printStackTrace();
            }
        }

        @Nullable
        private String retrieveConnectionInfo(int maxAgeDays, String stationId, String type, String locale) {
            try {
                FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), String.format(CONN_INFO_CACHE_FILENAME, stationId, type, locale)));
                ObjectInputStream is = new ObjectInputStream(fis);
                CachedConnectionInfo cached = (CachedConnectionInfo) is.readObject();
                is.close();
                fis.close();

                if (cached.date.getTime() < new Date().getTime() - 1000 * 60 * 60 * 24 * maxAgeDays && Connectivity.isConnected(context)) {
                    return null;
                }
                return cached.html;
            } catch (Exception e) {
                // oh well, we'll have to do without cache
                // caching is best-effort
                return null;
            }
        }
    }

    private static class CachedConnectionInfo implements Serializable {
        public String html;
        public Date date;

        public CachedConnectionInfo(String html) {
            this.html = html;
            this.date = new Date();
        }
    }

    public interface OnConnectionInfoReadyListener {
        void onSuccess(List<String> connectionInfo);

        void onProgress(int current);

        void onFailure();
    }

    public interface OnCacheAllListener {
        void onSuccess();

        void onProgress(int current, int total);

        void onFailure();
    }
}
