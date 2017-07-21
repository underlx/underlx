package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

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

        if (station.isAlwaysClosed()) {
            LinearLayout closedLayout = (LinearLayout) view.findViewById(R.id.closed_info_layout);
            closedLayout.setVisibility(View.VISIBLE);
        }

        // Connections
        TextView connectionsTitleView = (TextView) view.findViewById(R.id.connections_title_view);

        // buttons
        Button busButton = (Button) view.findViewById(R.id.connections_bus_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BUS)) {
            busButton.setVisibility(View.VISIBLE);
            busButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_BUS, station);
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
                    ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_BOAT, station);
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
                    ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_TRAIN, station);
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
        realm.close();
    }

    public interface OnFragmentInteractionListener {
        MainService getMainService();
    }

    private class ConnectionInfoReadyListener implements ExtraContentCache.OnConnectionInfoReadyListener {
        private Snackbar snackbar;

        @Override
        public void onSuccess(List<String> connectionInfo) {
            if (snackbar != null) {
                snackbar.dismiss();
            }
            if (getActivity() == null) {
                // our activity went away while we worked...
                return;
            }
            DialogFragment newFragment = HtmlDialogFragment.newInstance(connectionInfo.get(0));
            newFragment.show(getActivity().getSupportFragmentManager(), "conninfo");
        }

        @Override
        public void onProgress(int current) {
            if (getActivity() != null) {
                snackbar = Snackbar.make(getActivity().findViewById(R.id.fab), R.string.frag_station_conn_info_loading, Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
            }
        }

        @Override
        public void onFailure() {
            if (snackbar != null) {
                snackbar.dismiss();
            }
            if (getActivity() == null) {
                // our activity went away while we worked...
                return;
            }
            DialogFragment newFragment = HtmlDialogFragment.newInstance(getString(R.string.frag_station_info_unavailable));
            newFragment.show(getActivity().getSupportFragmentManager(), "conninfo");
        }
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
