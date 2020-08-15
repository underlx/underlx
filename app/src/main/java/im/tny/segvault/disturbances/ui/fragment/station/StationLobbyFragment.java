package im.tny.segvault.disturbances.ui.fragment.station;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.widget.NestedScrollView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.HashMap;
import java.util.Map;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.util.ScrollFixMapView;
import im.tny.segvault.disturbances.ui.widget.LobbyView;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.WorldPath;


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
    private NestedScrollView lobbyScrollView;

    private ScrollFixMapView mapView;
    private GoogleMap googleMap;
    private boolean mapLayoutReady = false;
    private Map<Lobby.Exit, Marker> markers = new HashMap<>();

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
        mapLayoutReady = false;
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_station_lobby, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(StationActivity.ACTION_MAIN_SERVICE_BOUND);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        lobbiesLayout = view.findViewById(R.id.lobbies_layout);
        lobbyScrollView = view.findViewById(R.id.lobby_scroll_view);

        mapView = view.findViewById(R.id.map_view);
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
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    private void update() {
        if (mListener == null)
            return;

        Network net = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
        if (net == null)
            return;
        final Station station = net.getStation(stationId);
        if (station == null)
            return;

        // Lobbies
        final int[] lobbyColors = Util.lobbyColors.clone();

        if (station.getLobbies().size() == 1) {
            lobbyColors[0] = Color.BLACK;
        }

        int curLobbyColorIdx = 0;
        for (Lobby lobby : station.getLobbies()) {
            LobbyView v = new LobbyView(getContext(), lobby, lobbyColors[curLobbyColorIdx]);
            v.setInteractionListener(exit -> {
                if (googleMap == null || !mapLayoutReady) {
                    return;
                }
                Marker marker = markers.get(exit);
                if (marker != null) {
                    marker.showInfoWindow();
                }
                if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    lobbyScrollView.fullScroll(NestedScrollView.FOCUS_UP);
                }
                googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(marker.getPosition(), googleMap.getCameraPosition().zoom)));
            });
            lobbiesLayout.addView(v);
            curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
        }

        final int preselExit = mListener.getPreselectedExitId();

        final ViewTreeObserver vto = mapView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    mapLayoutReady = true;
                    trySetupMap(station, lobbyColors, preselExit);
                    // remove the listener... or we'll be doing this a lot.
                    ViewTreeObserver obs = mapView.getViewTreeObserver();
                    obs.removeGlobalOnLayoutListener(this);
                }
            });
        }

        mapView.getMapAsync(mMap -> {
            googleMap = mMap;

            trySetupMap(station, lobbyColors, preselExit);
        });
    }

    private void trySetupMap(final Station station, final int[] lobbyColors, final int preselExit) {
        if (googleMap == null || !mapLayoutReady) {
            return;
        }
        if (!station.getAllTags().contains("c_train")) {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            getContext(), R.raw.no_train_stations_map_style));
        }

        markers.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int curLobbyColorIdx = 0;
        Marker focusMarker = null;
        for (Lobby lobby : station.getLobbies()) {
            for (Lobby.Exit exit : lobby.getExits()) {
                LatLng pos = new LatLng(exit.worldCoord[0], exit.worldCoord[1]);
                builder.include(pos);
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(String.format(getString(R.string.frag_station_lobby_name), lobby.getName()))
                        .snippet(exit.getExitsString())
                        .icon(Util.createMapMarker(getContext(),
                                Util.getDrawableResourceIdForExitType(exit.type, lobby.isAlwaysClosed()), lobbyColors[curLobbyColorIdx]))
                        .alpha(lobby.isAlwaysClosed() ? 0.5f : 1));
                if (exit.id == preselExit) {
                    marker.showInfoWindow();
                    focusMarker = marker;
                }
                markers.put(exit, marker);
            }
            curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
        }

        for (Line line : station.getNetwork().getLines()) {
            for (WorldPath path : line.getPaths()) {
                PolylineOptions options = new PolylineOptions().width(5).color(line.getColor()).geodesic(true);
                for (float[] point : path.getPath()) {
                    options.add(new LatLng(point[0], point[1]));
                }
                googleMap.addPolyline(options);
            }
        }

        // make sure we don't zoom in too close (if the points are too close together/there's a single point)
        LatLngBounds bounds = builder.build();
        LatLng center = bounds.getCenter();
        builder.include(new LatLng(center.latitude - 0.00025f, center.longitude - 0.00025f));
        builder.include(new LatLng(center.latitude + 0.00025f, center.longitude + 0.00025f));
        bounds = builder.build();

        int padding = 100; // offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        googleMap.moveCamera(cu);
        if (focusMarker != null) {
            googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(focusMarker.getPosition(), googleMap.getCameraPosition().zoom)));
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
        int getPreselectedExitId();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
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
