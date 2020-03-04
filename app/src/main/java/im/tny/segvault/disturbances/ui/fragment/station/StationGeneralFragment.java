package im.tny.segvault.disturbances.ui.fragment.station;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.ExtraContentCache;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MqttManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.StationUse;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.fragment.HtmlDialogFragment;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

import static android.content.Context.MODE_PRIVATE;


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

    private Station station;

    private OnFragmentInteractionListener mListener;
    private int mqttPartyID = -1;

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
        filter.addAction(MqttManager.ACTION_VEHICLE_ETAS_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        update();

        final Network net = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
        if (net == null) {
            return view;
        }
        final Station station = net.getStation(stationId);
        if (station == null) {
            return view;
        }

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
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) {
            return;
        }

        final MqttManager mqttManager = Coordinator.get(context).getMqttManager();

        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        mqttPartyID = mqttManager.connect(mqttManager.getAllVehicleETAsTopicForStation(networkId, stationId));
    }

    @Override
    public void onPause() {
        super.onPause();
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (mqttPartyID >= 0) {
            final MqttManager mqttManager = Coordinator.get(context).getMqttManager();
            mqttManager.disconnect(mqttPartyID);
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

        Network net = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
        if (net == null)
            return;
        station = net.getStation(stationId);
        if (station == null)
            return;

        if (station.isAlwaysClosed()) {
            LinearLayout closedLayout = view.findViewById(R.id.closed_info_layout);
            closedLayout.setVisibility(View.VISIBLE);
        } else if (station.isExceptionallyClosed(net, new Date())) {
            TextView closedView = view.findViewById(R.id.closed_info_view);
            Formatter f = new Formatter();
            DateUtils.formatDateRange(getContext(), f, station.getNextOpenTime(net), station.getNextOpenTime(net), DateUtils.FORMAT_SHOW_TIME, net.getTimezone().getID());
            closedView.setText(String.format(getString(R.string.frag_station_closed_schedule), f.toString()));

            LinearLayout closedLayout = view.findViewById(R.id.closed_info_layout);
            closedLayout.setVisibility(View.VISIBLE);
        }

        Map<String, List<API.MQTTvehicleETA>> etas = Coordinator.get(getContext()).getMqttManager().getVehicleETAsForStation(station, 3);

        LinearLayout vehicleETAsLayout = view.findViewById(R.id.vehicle_etas_layout);
        if (etas.size() > 0 && !station.isAlwaysClosed()) {
            TextView vehicleETAsView = view.findViewById(R.id.vehicle_etas_view);

            List<CharSequence> etaLines = MainService.getETAlines(getContext(), etas, station, null);
            vehicleETAsView.setText("");
            for (int i = 0; i < etaLines.size(); i++) {
                vehicleETAsView.append(etaLines.get(i));
                if (i < etaLines.size() - 1) {
                    vehicleETAsView.append("\n");
                }
            }
            vehicleETAsLayout.setVisibility(View.VISIBLE);
        } else {
            vehicleETAsLayout.setVisibility(View.GONE);
        }

        // Titles
        TextView connectionsTitleView = view.findViewById(R.id.connections_title_view);
        TextView accessibilityTitleView = view.findViewById(R.id.accessibility_title_view);
        TextView servicesTitleView = view.findViewById(R.id.services_title_view);

        // feature indication
        final Map<String, View> tagToView = new HashMap<>();
        final Map<String, View> tagToTitle = new HashMap<>();
        tagToView.put("a_store", view.findViewById(R.id.service_stores_layout));
        tagToTitle.put("a_store", servicesTitleView);
        tagToView.put("a_wc", view.findViewById(R.id.service_wc_layout));
        tagToTitle.put("a_wc", servicesTitleView);
        tagToView.put("a_baby", view.findViewById(R.id.service_baby_care_layout));
        tagToTitle.put("a_baby", servicesTitleView);
        tagToView.put("a_wifi", view.findViewById(R.id.service_wifi_layout));
        tagToTitle.put("a_wifi", servicesTitleView);
        tagToView.put("c_airport", view.findViewById(R.id.feature_airport_layout));
        tagToTitle.put("c_airport", connectionsTitleView);
        tagToView.put("c_bike", view.findViewById(R.id.service_bike_layout));
        tagToTitle.put("c_bike", servicesTitleView);
        tagToView.put("c_boat", view.findViewById(R.id.feature_boat_layout));
        tagToTitle.put("c_boat", connectionsTitleView);
        tagToView.put("c_bus", view.findViewById(R.id.feature_bus_layout));
        tagToTitle.put("c_bus", connectionsTitleView);
        tagToView.put("c_parking", view.findViewById(R.id.service_park_layout));
        tagToTitle.put("c_parking", servicesTitleView);
        tagToView.put("c_taxi", view.findViewById(R.id.feature_taxi_layout));
        tagToTitle.put("c_taxi", connectionsTitleView);
        tagToView.put("c_train", view.findViewById(R.id.feature_train_layout));
        tagToTitle.put("c_train", connectionsTitleView);
        tagToView.put("m_escalator_platform", view.findViewById(R.id.feature_escalator_platform_layout));
        tagToTitle.put("m_escalator_platform", accessibilityTitleView);
        tagToView.put("m_escalator_surface", view.findViewById(R.id.feature_escalator_surface_layout));
        tagToTitle.put("m_escalator_surface", accessibilityTitleView);
        tagToView.put("m_stepfree", view.findViewById(R.id.feature_stepfree_layout));
        tagToTitle.put("m_stepfree", accessibilityTitleView);
        tagToView.put("m_lift_platform", view.findViewById(R.id.feature_lift_platform_layout));
        tagToTitle.put("m_lift_platform", accessibilityTitleView);
        tagToView.put("m_lift_surface", view.findViewById(R.id.feature_lift_surface_layout));
        tagToTitle.put("m_lift_surface", accessibilityTitleView);
        tagToView.put("m_platform", view.findViewById(R.id.feature_wheelchair_platform_layout));
        tagToTitle.put("m_platform", accessibilityTitleView);
        tagToView.put("s_client", view.findViewById(R.id.service_client_space_layout));
        tagToTitle.put("s_client", servicesTitleView);
        tagToView.put("s_info", view.findViewById(R.id.service_info_space_layout));
        tagToTitle.put("s_info", servicesTitleView);
        tagToView.put("s_lostfound", view.findViewById(R.id.service_lostfound_layout));
        tagToTitle.put("s_lostfound", servicesTitleView);
        tagToView.put("s_navegante", view.findViewById(R.id.service_navegante_space_layout));
        tagToTitle.put("s_navegante", servicesTitleView);
        tagToView.put("s_ticket1", view.findViewById(R.id.service_ticket_office_layout));
        tagToTitle.put("s_ticket1", servicesTitleView);
        tagToView.put("s_ticket2", view.findViewById(R.id.service_ticket_office_layout));
        tagToTitle.put("s_ticket2", servicesTitleView);
        tagToView.put("s_ticket3", view.findViewById(R.id.service_ticket_office_layout));
        tagToTitle.put("s_ticket3", servicesTitleView);
        tagToView.put("s_urgent_pass", view.findViewById(R.id.service_urgent_tickets_layout));
        tagToTitle.put("s_urgent_pass", servicesTitleView);

        List<String> stationTags = station.getAllTags();
        for (String tag : stationTags) {
            View view = tagToView.get(tag);
            View titleView = tagToTitle.get(tag);
            if (view != null && titleView != null) {
                view.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.VISIBLE);
            }
        }

        // buttons
        Button busButton = view.findViewById(R.id.connections_bus_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BUS)) {
            busButton.setVisibility(View.VISIBLE);
            busButton.setOnClickListener(view -> ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_BUS, station));
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        Button boatButton = view.findViewById(R.id.connections_boat_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BOAT)) {
            boatButton.setVisibility(View.VISIBLE);
            boatButton.setOnClickListener(view -> ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_BOAT, station));
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        Button trainButton = view.findViewById(R.id.connections_train_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_TRAIN)) {
            trainButton.setVisibility(View.VISIBLE);
            trainButton.setOnClickListener(view -> ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_TRAIN, station));
            connectionsTitleView.setVisibility(View.VISIBLE);
        }

        Button parkButton = view.findViewById(R.id.connections_park_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_PARK)) {
            parkButton.setVisibility(View.VISIBLE);
            parkButton.setOnClickListener(view -> ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_PARK, station));
            servicesTitleView.setVisibility(View.VISIBLE);
        }

        Button bikeButton = view.findViewById(R.id.connections_bike_button);
        if (station.hasConnectionUrl(Station.CONNECTION_TYPE_BIKE)) {
            bikeButton.setVisibility(View.VISIBLE);
            bikeButton.setOnClickListener(view -> ExtraContentCache.getConnectionInfo(getContext(), new ConnectionInfoReadyListener(), Station.CONNECTION_TYPE_BIKE, station));
            servicesTitleView.setVisibility(View.VISIBLE);
        }

        new LoadStatsTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    private static class LoadStatsTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<StationGeneralFragment> parentRef;
        private long entryCount;
        private long exitCount;
        private long goneThroughCount;
        private long transferCount;

        LoadStatsTask(StationGeneralFragment parent) {
            this.parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            StationGeneralFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();

            entryCount = db.stationUseDao().countStationUsesOfType(parent.stationId, StationUse.UseType.NETWORK_ENTRY);
            exitCount = db.stationUseDao().countStationUsesOfType(parent.stationId, StationUse.UseType.NETWORK_EXIT);
            goneThroughCount = db.stationUseDao().countStationUsesOfType(parent.stationId, StationUse.UseType.GONE_THROUGH);
            transferCount = db.stationUseDao().countStationUsesOfType(parent.stationId, StationUse.UseType.INTERCHANGE);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            StationGeneralFragment parent = parentRef.get();
            if (parent == null || parent.getContext() == null || !parent.isAdded()) {
                return;
            }

            TextView statsEntryCountView = parent.view.findViewById(R.id.station_entry_count_view);
            statsEntryCountView.setText(String.format(parent.getString(R.string.frag_station_stats_entry), entryCount));

            TextView statsExitCountView = parent.view.findViewById(R.id.station_exit_count_view);
            statsExitCountView.setText(String.format(parent.getString(R.string.frag_station_stats_exit), exitCount));

            TextView statsGoneThroughCountView = parent.view.findViewById(R.id.station_gone_through_count_view);
            statsGoneThroughCountView.setText(String.format(parent.getString(R.string.frag_station_stats_gone_through), goneThroughCount));

            TextView statsTransferCountView = parent.view.findViewById(R.id.station_transfer_count_view);
            if (parent.station.getLines().size() > 1) {
                statsTransferCountView.setText(String.format(parent.getString(R.string.frag_station_stats_transfer), transferCount));
                statsTransferCountView.setVisibility(View.VISIBLE);
            } else {
                statsTransferCountView.setVisibility(View.GONE);
            }
        }
    }

    public interface OnFragmentInteractionListener {
    }

    private class ConnectionInfoReadyListener implements ExtraContentCache.OnConnectionInfoReadyListener {
        private Snackbar snackbar;

        @Override
        public void onSuccess(List<String> connectionInfo) {
            if (getActivity() == null) {
                // our activity went away while we worked...
                return;
            }
            if (snackbar != null) {
                snackbar.dismiss();
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
            if (getActivity() == null) {
                // our activity went away while we worked...
                return;
            }
            if (snackbar != null) {
                snackbar.dismiss();
            }
            DialogFragment newFragment = HtmlDialogFragment.newInstance(getString(R.string.frag_station_info_unavailable));
            newFragment.show(getActivity().getSupportFragmentManager(), "conninfo");
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case StationActivity.ACTION_MAIN_SERVICE_BOUND:
                case MqttManager.ACTION_VEHICLE_ETAS_UPDATED:
                    if (mListener != null) {
                        update();
                    }
                    break;
            }
        }
    };
}
