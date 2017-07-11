package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sufficientlysecure.htmltextview.HtmlTextView;

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
import java.util.Date;
import java.util.Locale;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link StationTriviaFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link StationTriviaFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StationTriviaFragment extends Fragment {
    private static final String ARG_STATION_ID = "stationId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String stationId;
    private String networkId;

    private OnFragmentInteractionListener mListener;

    private HtmlTextView triviaView;

    public StationTriviaFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param networkId Network ID
     * @param stationId Station ID
     * @return A new instance of fragment StationLobbyFragment.
     */
    public static StationTriviaFragment newInstance(String networkId, String stationId) {
        StationTriviaFragment fragment = new StationTriviaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        args.putString(ARG_STATION_ID, stationId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stationId = getArguments().getString(ARG_STATION_ID);
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_station_trivia, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(StationActivity.ACTION_MAIN_SERVICE_BOUND);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        triviaView = (HtmlTextView) view.findViewById(R.id.trivia_view);

        triviaView.setHtml(getString(R.string.status_loading));
        update();

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void update() {
        if (mListener == null)
            return;
        MainService service = mListener.getMainService();
        if (service == null)
            return;

        Network net = service.getNetwork(networkId);
        Station station = net.getStation(stationId);

        new RetrieveTriviaTask().execute(station);
    }

    private class RetrieveTriviaTask extends AsyncTask<Station, Void, String> {
        @Override
        protected String doInBackground(Station... arrStation) {
            Locale l = Util.getCurrentLocale(getContext());
            String lang = l.getLanguage();
            String url = arrStation[0].getTriviaURLforLocale(lang);
            if(url == null) {
                lang = "en";
                url = arrStation[0].getTriviaURLforLocale(lang);
                if(url == null) {
                    return null;
                }
            }
            String response = retrieveTrivia(7, lang);
            if(response != null) {
                return response;
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
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null)
                    sb.append(line + "\n");

                response = sb.toString();
                cacheTrivia(response, lang);
            } catch (IOException e) {
                return null;
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                triviaView.setHtml(getString(R.string.frag_station_info_unavailable));
            } else {
                triviaView.setHtml(result);
            }
        }

        // cache mechanism
        private final String TRIVIA_CACHE_FILENAME = "TriviaCache-%s-%s";

        private void cacheTrivia(String trivia, String locale) {
            CachedTrivia toCache = new CachedTrivia(trivia);
            try {
                FileOutputStream fos = new FileOutputStream(new File(getContext().getCacheDir(), String.format(TRIVIA_CACHE_FILENAME, stationId, locale)));
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
        private String retrieveTrivia(int maxAgeDays, String locale) {
            try {
                FileInputStream fis = new FileInputStream(new File(getContext().getCacheDir(), String.format(TRIVIA_CACHE_FILENAME, stationId, locale)));
                ObjectInputStream is = new ObjectInputStream(fis);
                CachedTrivia cached = (CachedTrivia) is.readObject();
                is.close();
                fis.close();

                if (cached.date.getTime() < new Date().getTime() - 1000 * 60 * 60 * 24 * maxAgeDays && Connectivity.isConnected(getContext())) {
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

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case StationActivity.ACTION_MAIN_SERVICE_BOUND:
                    if (mListener != null) {
                        update();
                    }
                    break;
            }
        }
    };
}
