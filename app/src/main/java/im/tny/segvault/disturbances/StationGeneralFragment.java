package im.tny.segvault.disturbances;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import io.realm.Realm;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link StationGeneralFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link StationGeneralFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StationGeneralFragment extends Fragment {
    private static final String ARG_STATION_ID = "stationId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String stationId;
    private String networkId;

    private OnFragmentInteractionListener mListener;

    private View view;

    public StationGeneralFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param networkId Network ID
     * @param stationId Station ID
     * @return A new instance of fragment StationGeneralFragment.
     */
    public static StationGeneralFragment newInstance(String networkId, String stationId) {
        StationGeneralFragment fragment = new StationGeneralFragment();
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
        view = inflater.inflate(R.layout.fragment_station_general, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(StationActivity.ACTION_MAIN_SERVICE_BOUND);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

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
        final Station station = net.getStation(stationId);

        // Connections
        TextView connectionsTitleView = (TextView) view.findViewById(R.id.connections_title_view);

        // buttons
        Button busButton = (Button) view.findViewById(R.id.connections_bus_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BUS)) {
            busButton.setVisibility(View.VISIBLE);
            busButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new RetrieveConnectionInfoTask(Station.CONNECTION_TYPE_BUS).execute(station);
                }
            });
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        Button boatButton = (Button) view.findViewById(R.id.connections_boat_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BOAT)) {
            boatButton.setVisibility(View.VISIBLE);
            boatButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new RetrieveConnectionInfoTask(Station.CONNECTION_TYPE_BOAT).execute(station);
                }
            });
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        Button trainButton = (Button) view.findViewById(R.id.connections_train_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_TRAIN)) {
            trainButton.setVisibility(View.VISIBLE);
            trainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new RetrieveConnectionInfoTask(Station.CONNECTION_TYPE_TRAIN).execute(station);
                }
            });
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        // icons
        LinearLayout busLayout = (LinearLayout) view.findViewById(R.id.feature_bus_layout);
        if (station.getFeatures().bus) {
            busLayout.setVisibility(View.VISIBLE);
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        LinearLayout boatLayout = (LinearLayout) view.findViewById(R.id.feature_boat_layout);
        if (station.getFeatures().boat) {
            boatLayout.setVisibility(View.VISIBLE);
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        LinearLayout trainLayout = (LinearLayout) view.findViewById(R.id.feature_train_layout);
        if (station.getFeatures().train) {
            trainLayout.setVisibility(View.VISIBLE);
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        LinearLayout airportLayout = (LinearLayout) view.findViewById(R.id.feature_airport_layout);
        if (station.getFeatures().airport) {
            airportLayout.setVisibility(View.VISIBLE);
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        // Accessibility
        TextView accessibilityTitleView = (TextView) view.findViewById(R.id.accessibility_title_view);
        LinearLayout liftLayout = (LinearLayout) view.findViewById(R.id.feature_lift_layout);
        if (station.getFeatures().lift) {
            liftLayout.setVisibility(View.VISIBLE);
            accessibilityTitleView.setVisibility(View.VISIBLE);
        }

        // Services
        TextView servicesTitleView = (TextView) view.findViewById(R.id.services_title_view);
        LinearLayout wifiLayout = (LinearLayout) view.findViewById(R.id.service_wifi_layout);
        if (station.getFeatures().wifi) {
            wifiLayout.setVisibility(View.VISIBLE);
            servicesTitleView.setVisibility(View.VISIBLE);
        }

        // Statistics
        Realm realm = Realm.getDefaultInstance();
        TextView statsEntryCountView = (TextView) view.findViewById(R.id.station_entry_count_view);
        long entryCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_ENTRY.name()).count();
        statsEntryCountView.setText(String.format(getString(R.string.frag_station_stats_entry), entryCount));

        TextView statsExitCountView = (TextView) view.findViewById(R.id.station_exit_count_view);
        long exitCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_EXIT.name()).count();
        statsExitCountView.setText(String.format(getString(R.string.frag_station_stats_exit), exitCount));

        TextView statsGoneThroughCountView = (TextView) view.findViewById(R.id.station_gone_through_count_view);
        long goneThroughCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.GONE_THROUGH.name()).count();
        statsGoneThroughCountView.setText(String.format(getString(R.string.frag_station_stats_gone_through), goneThroughCount));

        TextView statsTransferCountView = (TextView) view.findViewById(R.id.station_transfer_count_view);
        if (station.getLines().size() > 1) {
            long transferCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.INTERCHANGE.name()).count();
            statsTransferCountView.setText(String.format(getString(R.string.frag_station_stats_transfer), transferCount));
            statsTransferCountView.setVisibility(View.VISIBLE);
        } else {
            statsTransferCountView.setVisibility(View.GONE);
        }
    }

    public static class ConnectionsDialogFragment extends DialogFragment {
        private static final String ARG_HTML = "html";

        public static ConnectionsDialogFragment newInstance(String html) {
            ConnectionsDialogFragment fragment = new ConnectionsDialogFragment();
            Bundle args = new Bundle();
            args.putString(ARG_HTML, html);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String html = "";
            if (getArguments() != null) {
                html = getArguments().getString(ARG_HTML);
            }
            LayoutInflater inflater = getActivity().getLayoutInflater();

            View view = inflater.inflate(R.layout.dialog_connections, null);

            HtmlTextView htmltv = (HtmlTextView) view.findViewById(R.id.html_view);
            htmltv.setHtml(html);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(view);
            return builder.create();
        }
    }

    private class RetrieveConnectionInfoTask extends AsyncTask<Station, Void, String> {
        private String type;
        private Snackbar snackbar = null;

        RetrieveConnectionInfoTask(String type) {
            this.type = type;
        }

        @Override
        protected String doInBackground(Station... arrStation) {
            Locale l = Util.getCurrentLocale(getContext());
            String lang = l.getLanguage();
            String url = arrStation[0].getConnectionURLforLocale(type, lang);
            if (url == null) {
                lang = "en";
                url = arrStation[0].getConnectionURLforLocale(type, lang);
                if (url == null) {
                    return null;
                }
            }
            String response = retrieveConnectionInfo(7, type, lang);
            if (response != null) {
                return response;
            }
            publishProgress();
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
                cacheConnectionInfo(response, type, lang);
            } catch (IOException e) {
                return null;
            }
            return response;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            snackbar = Snackbar.make(getActivity().findViewById(R.id.fab), R.string.frag_station_conn_info_loading, Snackbar.LENGTH_INDEFINITE);
            snackbar.show();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                result = getString(R.string.frag_station_info_unavailable);
            }
            if(getActivity() == null) {
                // our activity went away while we worked...
                return;
            }
            if (snackbar != null) {
                snackbar.dismiss();
            }
            DialogFragment newFragment = ConnectionsDialogFragment.newInstance(result);
            newFragment.show(getActivity().getSupportFragmentManager(), "conninfo");
        }

        // cache mechanism
        private final String CONN_INFO_CACHE_FILENAME = "ConnCache-%s-%s-%s";

        private void cacheConnectionInfo(String trivia, String type, String locale) {
            CachedConnectionInfo toCache = new CachedConnectionInfo(trivia);
            try {
                FileOutputStream fos = new FileOutputStream(new File(getContext().getCacheDir(), String.format(CONN_INFO_CACHE_FILENAME, stationId, type, locale)));
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
        private String retrieveConnectionInfo(int maxAgeDays, String type, String locale) {
            try {
                FileInputStream fis = new FileInputStream(new File(getContext().getCacheDir(), String.format(CONN_INFO_CACHE_FILENAME, stationId, type, locale)));
                ObjectInputStream is = new ObjectInputStream(fis);
                CachedConnectionInfo cached = (CachedConnectionInfo) is.readObject();
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

    private static class CachedConnectionInfo implements Serializable {
        public String html;
        public Date date;

        public CachedConnectionInfo(String html) {
            this.html = html;
            this.date = new Date();
        }
    }


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
