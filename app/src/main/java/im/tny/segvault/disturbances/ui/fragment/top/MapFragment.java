package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.BuildConfig;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.util.CustomWebView;
import im.tny.segvault.disturbances.ui.util.ScrollFixMapView;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.WorldPath;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

import static android.content.Context.MODE_PRIVATE;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MapFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;

    private static final String ARG_NETWORK_ID = "networkId";

    private FrameLayout container;

    private String networkId;
    private Network network;

    private MapStrategy currentMapStrategy;

    private boolean mockLocationMode = false;

    private Bundle savedInstanceState;

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance(String networkId) {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_map_title), R.id.nav_map, false, false);
        setHasOptionsMenu(true);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        this.container = (FrameLayout) view.findViewById(R.id.map_container);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        this.savedInstanceState = savedInstanceState;

        tryLoad();
        return view;
    }

    private void tryLoad() {
        if (network != null || mListener == null || mListener.getMainService() == null) {
            return;
        }
        network = mListener.getMainService().getNetwork(networkId);
        List<Network.Plan> maps = network.getMaps();
        switchMap(maps, false);
    }

    private void switchMap(List<Network.Plan> maps, boolean next) {
        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        int mapIndex = sharedPref.getInt(PreferenceNames.MapType, 0);
        if (next) {
            mapIndex++;
        }
        if (mapIndex > maps.size() - 1) {
            mapIndex = 0;
        }
        if (next) {
            SharedPreferences.Editor e = sharedPref.edit();
            e.putInt(PreferenceNames.MapType, mapIndex);
            e.apply();
        }

        Network.Plan selectedMap = maps.get(mapIndex);

        MapStrategy strategy = null;
        if (selectedMap instanceof Network.HtmlDiagram) {
            strategy = new WebViewMapStrategy();
        } else if (selectedMap instanceof Network.WorldMap) {
            strategy = new GoogleMapsMapStrategy();
        }
        if (strategy == null) {
            return;
        }
        if (currentMapStrategy instanceof WebViewMapStrategy && strategy instanceof WebViewMapStrategy) {
            // faster map switching when switching between HtmlDiagrams
            ((WebViewMapStrategy) currentMapStrategy).switchMap((Network.HtmlDiagram) selectedMap);
        } else {
            container.removeAllViews();
            currentMapStrategy = strategy;
            currentMapStrategy.setMockLocation(mockLocationMode);
            currentMapStrategy.initialize(container, selectedMap);
        }
    }

    private abstract class MapStrategy {
        abstract public void initialize(FrameLayout parent, Network.Plan map);

        public void zoomIn() {
        }

        public void zoomOut() {
        }

        private boolean canMock = false;

        public void setMockLocation(boolean allowMock) {
            canMock = allowMock;
        }

        public boolean getMockLocation() {
            return canMock;
        }

        public void onResume() {
        }

        public void onPause() {
        }

        public void onDestroy() {
        }

        public void onLowMemory() {
        }
    }

    private class WebViewMapStrategy extends MapStrategy {
        private WebView webview;

        @Override
        public void initialize(FrameLayout parent, Network.Plan map) {
            webview = new CustomWebView(getContext());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            webview.setLayoutParams(lp);
            parent.addView(webview);

            WebSettings webSettings = webview.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webview.addJavascriptInterface(new MapWebInterface(getContext()), "android");

            webview.getSettings().setLoadWithOverviewMode(true);
            webview.getSettings().setSupportZoom(true);
            webview.getSettings().setBuiltInZoomControls(true);
            webview.getSettings().setDisplayZoomControls(false);

            Network.HtmlDiagram m = (Network.HtmlDiagram) map;
            webview.getSettings().setUseWideViewPort(m.needsWideViewport());
            webview.loadUrl(m.getUrl());
        }

        @Override
        public void zoomIn() {
            webview.zoomIn();
        }

        @Override
        public void zoomOut() {
            webview.zoomOut();
        }

        public void switchMap(Network.HtmlDiagram map) {
            webview.getSettings().setUseWideViewPort(map.needsWideViewport());
            webview.loadUrl(map.getUrl());
        }
    }

    private class GoogleMapsMapStrategy extends MapStrategy implements
            OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnInfoWindowClickListener {
        private ScrollFixMapView mapView;
        private GoogleMap googleMap;
        private List<Marker> stationMarkers = new ArrayList<>();
        private List<Marker> lobbyMarkers = new ArrayList<>();
        private Map<Marker, String> markerLinks = new HashMap<>();

        @Override
        public void initialize(FrameLayout parent, Network.Plan map) {
            mapView = new ScrollFixMapView(getContext());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(lp);
            parent.addView(mapView);

            mapView.onCreate(savedInstanceState);

            mapView.onResume(); // needed to get the map to display immediately

            mapView.getMapAsync(this);
        }

        @Override
        public void zoomIn() {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        }

        @Override
        public void zoomOut() {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            this.googleMap = googleMap;
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            googleMap.setOnCameraIdleListener(this);
            googleMap.setOnInfoWindowClickListener(this);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            for(Line line : network.getLines()) {
                for(WorldPath path : line.getPaths()) {
                    PolylineOptions options = new PolylineOptions().width(6).color(line.getColor()).geodesic(true);
                    for(float[] point : path.getPath()) {
                        options.add(new LatLng(point[0], point[1]));
                    }
                    googleMap.addPolyline(options);
                }
            }

            stationMarkers.clear();
            lobbyMarkers.clear();
            markerLinks.clear();
            for(Station station : network.getStations()) {
                LatLng pos = new LatLng(station.getWorldCoordinates()[0], station.getWorldCoordinates()[1]);
                builder.include(pos);
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .icon(Util.getBitmapDescriptorFromVector(getContext(), R.drawable.ic_station_dot))
                        .title(station.getName())
                .snippet(getString(R.string.frag_map_action_more)));
                stationMarkers.add(marker);
                markerLinks.put(marker, "station:" + station.getId());

                // lobbies
                final int[] lobbyColors = Util.lobbyColors.clone();
                if (station.getLobbies().size() == 1) {
                    lobbyColors[0] = Color.BLACK;
                }
                int curLobbyColorIdx = 0;
                for (Lobby lobby : station.getLobbies()) {
                    if (lobby.isAlwaysClosed()) {
                        continue;
                    }
                    for (Lobby.Exit exit : lobby.getExits()) {
                        pos = new LatLng(exit.worldCoord[0], exit.worldCoord[1]);
                        builder.include(pos);
                        marker = googleMap.addMarker(new MarkerOptions()
                                .position(pos)
                                .title(String.format(getString(R.string.frag_map_lobby_name), station.getName(), lobby.getName()))
                                .snippet(getString(R.string.frag_map_action_more))
                                .icon(Util.getBitmapDescriptorFromVector(getContext(), R.drawable.map_marker_exit, lobbyColors[curLobbyColorIdx])));
                        marker.setVisible(false); // only shown when zoomed in past a certain level
                        lobbyMarkers.add(marker);
                        markerLinks.put(marker, "station:" + station.getId() + ":lobby:" + lobby.getId() + ":exit:" + exit.id);
                    }
                    curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
                }
            }

            LatLngBounds bounds = builder.build();
            int padding = 64; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            googleMap.moveCamera(cu);
        }

        @Override
        public void onResume() {
            if (mapView != null) {
                mapView.onResume();
            }
        }

        @Override
        public void onPause() {
            if (mapView != null) {
                mapView.onPause();
            }
        }

        @Override
        public void onDestroy() {
            if (mapView != null) {
                mapView.onDestroy();
            }
        }

        @Override
        public void onLowMemory() {
            if (mapView != null) {
                mapView.onLowMemory();
            }
        }

        @Override
        public void onCameraIdle() {
            if(googleMap.getCameraPosition().zoom >= 15 && !stationMarkers.isEmpty() && stationMarkers.get(0).isVisible()) {
                for(Marker marker : stationMarkers) {
                    marker.setVisible(false);
                }
                for(Marker marker : lobbyMarkers) {
                    marker.setVisible(true);
                }
            } else if(googleMap.getCameraPosition().zoom < 15 && !stationMarkers.isEmpty() && !stationMarkers.get(0).isVisible()) {
                for(Marker marker : stationMarkers) {
                    marker.setVisible(true);
                }
                for(Marker marker : lobbyMarkers) {
                    marker.setVisible(false);
                }
            }
        }

        @Override
        public void onInfoWindowClick(Marker marker) {
            String link = markerLinks.get(marker);
            if (link == null) {
                return;
            }

            String[] parts = link.split(":");
            switch (parts[0]) {
                case "station":
                    if (parts.length > 3 && parts[2].equals("lobby")) {
                        if (parts.length > 5 && parts[4].equals("exit")) {
                            mListener.onStationLinkClicked(parts[1], parts[3], parts[5]);
                        } else {
                            mListener.onStationLinkClicked(parts[1], parts[3]);
                        }
                    } else {
                        mListener.onStationLinkClicked(parts[1]);
                    }
                    break;
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.map, menu);

        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        if (sharedPref != null) {
            if (BuildConfig.DEBUG && sharedPref.getBoolean(PreferenceNames.DeveloperMode, false)) {
                menu.findItem(R.id.menu_mock_location).setVisible(true);
            }
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                showTargetPrompt();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (currentMapStrategy == null) {
            // not yet ready
            return super.onOptionsItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.menu_zoom_out:
                currentMapStrategy.zoomOut();
                return true;
            case R.id.menu_zoom_in:
                currentMapStrategy.zoomIn();
                return true;
            case R.id.menu_swap_map:
                if (network != null) {
                    switchMap(network.getMaps(), true);
                }
                return true;
            case R.id.menu_mock_location:
                mockLocationMode = !item.isChecked();
                item.setChecked(mockLocationMode);
                if (currentMapStrategy != null) {
                    currentMapStrategy.setMockLocation(mockLocationMode);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
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
        if (currentMapStrategy != null) {
            currentMapStrategy.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentMapStrategy != null) {
            currentMapStrategy.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentMapStrategy != null) {
            currentMapStrategy.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (currentMapStrategy != null) {
            currentMapStrategy.onLowMemory();
        }
    }

    private void showTargetPrompt() {
        Context context = getContext();
        boolean isFirstOpen = false;
        if (context != null) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
            if (sharedPref != null) {
                isFirstOpen = sharedPref.getBoolean("fuse_first_map_open", true);
            }
        }

        if (!isFirstOpen) {
            return;
        }

        new MaterialTapTargetPrompt.Builder(getActivity())
                .setTarget(R.id.menu_swap_map)
                .setPrimaryText(R.string.frag_map_switch_type_taptarget_title)
                .setSecondaryText(R.string.frag_map_switch_type_taptarget_subtitle)
                .setPromptStateChangeListener(new MaterialTapTargetPrompt.PromptStateChangeListener() {
                    @Override
                    public void onPromptStateChanged(MaterialTapTargetPrompt prompt, int state) {
                        if (state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                            // User has pressed the prompt target
                            Context context = getContext();
                            if (context != null) {
                                SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
                                SharedPreferences.Editor e = sharedPref.edit();
                                e.putBoolean("fuse_first_map_open", false);
                                e.apply();
                            }
                        }
                    }
                })
                .setFocalColour(ContextCompat.getColor(getContext(), R.color.colorAccent))
                .setBackgroundColour(ContextCompat.getColor(getContext(), R.color.colorPrimaryLight))
                .show();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public class MapWebInterface {
        Context mContext;
        ObjectMapper mapper = new ObjectMapper();

        /**
         * Instantiate the interface and set the context
         */
        MapWebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onStationClicked(String id) {
            if (currentMapStrategy.getMockLocation()) {
                if (mListener == null)
                    return;
                MainService service = mListener.getMainService();
                if (service == null)
                    return;
                Network net = service.getNetwork(networkId);
                if (net != null) {
                    service.mockLocation(net.getStation(id));
                }
            } else {
                Intent intent = new Intent(getContext(), StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, id);
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, networkId);
                startActivity(intent);
            }
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
    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
        void onStationLinkClicked(String destination);
        void onStationLinkClicked(String destination, String lobby);
        void onStationLinkClicked(String destination, String lobby, String exit);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    tryLoad();
                    break;
            }
        }
    };
}
