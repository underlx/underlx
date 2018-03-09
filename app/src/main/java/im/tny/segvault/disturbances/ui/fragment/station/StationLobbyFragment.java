package im.tny.segvault.disturbances.ui.fragment.station;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.util.ScrollFixMapView;
import im.tny.segvault.disturbances.ui.widget.LobbyView;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link StationLobbyFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link StationLobbyFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StationLobbyFragment extends Fragment {
    private static final String ARG_STATION_ID = "stationId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String stationId;
    private String networkId;

    private OnFragmentInteractionListener mListener;

    private LinearLayout lobbiesLayout;

    private ScrollFixMapView mapView;
    private GoogleMap googleMap;

    public StationLobbyFragment() {
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
    public static StationLobbyFragment newInstance(String networkId, String stationId) {
        StationLobbyFragment fragment = new StationLobbyFragment();
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
        View view = inflater.inflate(R.layout.fragment_station_lobby, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(StationActivity.ACTION_MAIN_SERVICE_BOUND);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        lobbiesLayout = (LinearLayout) view.findViewById(R.id.lobbies_layout);

        mapView = (ScrollFixMapView) view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        mapView.onResume(); // needed to get the map to display immediately

        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }


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

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    private void update() {
        if (mListener == null)
            return;
        MainService service = mListener.getMainService();
        if (service == null)
            return;

        Network net = service.getNetwork(networkId);
        if (net == null)
            return;
        final Station station = net.getStation(stationId);
        if (station == null)
            return;

        // Lobbies
        final int[] lobbyColors = new int[]{
                Color.parseColor("#C040CE"),
                Color.parseColor("#4CAF50"),
                Color.parseColor("#142382"),
                Color.parseColor("#E0A63A"),
                Color.parseColor("#F15D2A")};

        if (station.getLobbies().size() == 1) {
            lobbyColors[0] = Color.BLACK;
        }

        int curLobbyColorIdx = 0;
        for (Lobby lobby : station.getLobbies()) {
            LobbyView v = new LobbyView(getContext(), lobby, lobbyColors[curLobbyColorIdx]);
            lobbiesLayout.addView(v);
            curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
        }

        final float[] preselExitCoords = mListener.getPreselectedExitCoords();

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap mMap) {
                googleMap = mMap;

                if (!station.getFeatures().train) {
                    googleMap.setMapStyle(
                            MapStyleOptions.loadRawResourceStyle(
                                    getContext(), R.raw.no_train_stations_map_style));
                }

                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                int curLobbyColorIdx = 0;
                for (Lobby lobby : station.getLobbies()) {
                    for (Lobby.Exit exit : lobby.getExits()) {
                        int markerRes = R.drawable.map_marker_exit;
                        float alpha = 1;
                        if (lobby.isAlwaysClosed()) {
                            markerRes = R.drawable.map_marker_exit_closed;
                            alpha = 0.5f;
                        }
                        LatLng pos = new LatLng(exit.worldCoord[0], exit.worldCoord[1]);
                        builder.include(pos);
                        Marker marker = googleMap.addMarker(new MarkerOptions()
                                .position(pos)
                                .title(String.format(getString(R.string.frag_station_lobby_name), lobby.getName()))
                                .snippet(exit.getExitsString())
                                .icon(Util.getBitmapDescriptorFromVector(getContext(), markerRes, lobbyColors[curLobbyColorIdx]))
                                .alpha(alpha));
                        if(preselExitCoords != null &&
                                preselExitCoords[0] == exit.worldCoord[0] && preselExitCoords[1] == exit.worldCoord[1]) {
                            marker.showInfoWindow();
                        }
                    }
                    curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
                }

                LatLngBounds bounds = builder.build();
                int padding = 100; // offset from edges of the map in pixels
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                googleMap.moveCamera(cu);
            }
        });
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
        float[] getPreselectedExitCoords();
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
