package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
                listener.onSuccessful(result);
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
        void onSuccessful(List<String> trivia);
        void onProgress(int current);
        void onFailure();
    }

    private static class RetrieveConnectionInfoTask extends AsyncTask<Station, Integer, List<String>> {
        private String type;
        private Context context;
        private OnConnectionInfoReadyListener listener;
        private int count;

        RetrieveConnectionInfoTask(Context context, OnConnectionInfoReadyListener listener, String type) {
            this.context = context;
            this.listener = listener;
            this.type = type;
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
                String url = station.getConnectionURLforLocale(type, lang);
                if (url == null) {
                    lang = "en";
                    url = station.getConnectionURLforLocale(type, lang);
                    if (url == null) {
                        continue;
                    }
                }
                String response = retrieveConnectionInfo(7, station.getId(), type, lang);
                if (response != null) {
                    retList.add(response);
                    continue;
                }
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
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
            return retList;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            listener.onProgress(values[0]);
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if(result == null || result.size() != count) {
                listener.onFailure();
            } else {
                listener.onSuccessful(result);
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
        void onSuccessful(List<String> connectionInfo);
        void onProgress(int current);
        void onFailure();
    }
}
