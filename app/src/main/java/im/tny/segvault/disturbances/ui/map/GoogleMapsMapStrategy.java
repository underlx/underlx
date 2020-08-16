package im.tny.segvault.disturbances.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.InternalLinkHandler;
import im.tny.segvault.disturbances.MqttManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.disturbances.ui.util.ScrollFixMapView;
import im.tny.segvault.disturbances.ui.util.TrainIconGenerator;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.WorldPath;

public class GoogleMapsMapStrategy extends MapStrategy implements
        OnMapReadyCallback,
        GoogleMap.OnCameraIdleListener,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.InfoWindowAdapter,
        GoogleMap.OnMarkerClickListener,
        MqttManager.OnVehiclePositionsReceivedListener {
    private Context context;
    private LayoutInflater layoutInflater;
    private Network network;
    private OnRequestPermissionsListener permListener;
    private UIthreadRunner uiThreadRunner;

    private ScrollFixMapView mapView;
    private GoogleMap googleMap;
    private TrainIconGenerator iconFactory;
    private Map<Marker, Station> stationMarkers = new HashMap<>();
    private Map<Marker, Lobby> exitMarkers = new HashMap<>();
    private Map<Marker, Station> stationsOfExitMarkers = new HashMap<>();
    private Map<Marker, Lobby.Exit> exitsOfExitMarkers = new HashMap<>();
    private Map<Marker, String> markerLinks = new HashMap<>();
    private Map<Line, Polyline> linePolylines = new HashMap<>();
    private Map<Polyline, Map<Station, Integer>> polylinePointIdxForStation = new HashMap<>();
    private Map<String, Circle> vehicleCircles = new HashMap<>();
    private Map<String, Marker> vehicleMarkers = new HashMap<>();
    private boolean mapLayoutReady = false;
    private boolean mapReady = false;
    private boolean mapReadyWaitingOnLayout = false;
    private Bundle savedInstanceState;

    private int mqttPartyID = -1;

    private static final double VEHICLE_MARKERS_ZOOM_THRESHOLD = 13.5;

    public interface OnRequestPermissionsListener {
        void onRequestPermissions();
    }

    public interface UIthreadRunner {
        void runOnUiThread(Runnable action);
    }

    public GoogleMapsMapStrategy(Context context, LayoutInflater layoutInflater, Network network, OnRequestPermissionsListener l, UIthreadRunner uiThreadRunner) {
        this.context = context;
        this.network = network;
        this.layoutInflater = layoutInflater;
        this.permListener = l;
        this.uiThreadRunner = uiThreadRunner;
    }

    @Override
    public void initialize(FrameLayout parent, Network.Plan map, @Nullable Bundle savedInstanceState) {
        this.savedInstanceState = savedInstanceState;
        iconFactory = new TrainIconGenerator(context);
        mapView = new ScrollFixMapView(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mapView.setLayoutParams(lp);
        parent.addView(mapView);

        final ViewTreeObserver vto = mapView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    mapLayoutReady = true;
                    if (mapReady || mapReadyWaitingOnLayout) {
                        onMapReady(googleMap);
                    }
                    // remove the listener... or we'll be doing this a lot.
                    ViewTreeObserver obs = mapView.getViewTreeObserver();
                    obs.removeGlobalOnLayoutListener(this);
                }
            });
        }

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public boolean isFilterable() {
        return true;
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
        if (!mapLayoutReady) {
            mapReadyWaitingOnLayout = true;
            return;
        }
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            permListener.onRequestPermissions();
        }
        googleMap.setOnCameraIdleListener(this);
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnInfoWindowClickListener(this);
        googleMap.setInfoWindowAdapter(this);

        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        putPolylines();
        putMarkers(network.getStations(), builder);

        CameraUpdate cu;
        if (savedInstanceState == null) {
            LatLngBounds bounds = builder.build();
            int padding = 64; // offset from edges of the map in pixels
            cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        } else {
            cu = CameraUpdateFactory.newCameraPosition(
                    savedInstanceState.getParcelable(STATE_CAMERA_POSITION));
        }
        googleMap.moveCamera(cu);

        // we can now begin adding train markers
        mapReady = true;
        checkShowTrains();
    }

    private int getLocationIndex(LatLng pos, List<LatLng> points) {
        int nearestIdx = -1;
        for (int tolerance = 10; tolerance < 150 && nearestIdx < 0; tolerance += 10) {
            nearestIdx = PolyUtil.locationIndexOnEdgeOrPath(pos, points, false, true, tolerance);
        }
        return nearestIdx;
    }

    private LatLng getMarkerProjectionOnSegment(LatLng pos, List<LatLng> points) {
        int nearestIdx = getLocationIndex(pos, points);
        if (nearestIdx < 0) {
            return null;
        }

        return findNearestPoint(pos,
                points.get(nearestIdx),
                points.get(nearestIdx + 1));
    }

    private LatLng findNearestPoint(final LatLng p, final LatLng start, final LatLng end) {
        if (start.equals(end)) {
            return start;
        }

        final double s0lat = Math.toRadians(p.latitude);
        final double s0lng = Math.toRadians(p.longitude);
        final double s1lat = Math.toRadians(start.latitude);
        final double s1lng = Math.toRadians(start.longitude);
        final double s2lat = Math.toRadians(end.latitude);
        final double s2lng = Math.toRadians(end.longitude);

        double s2s1lat = s2lat - s1lat;
        double s2s1lng = s2lng - s1lng;
        final double u = ((s0lat - s1lat) * s2s1lat + (s0lng - s1lng) * s2s1lng)
                / (s2s1lat * s2s1lat + s2s1lng * s2s1lng);
        if (u <= 0) {
            return start;
        }
        if (u >= 1) {
            return end;
        }

        return new LatLng(start.latitude + (u * (end.latitude - start.latitude)),
                start.longitude + (u * (end.longitude - start.longitude)));
    }

    private void putPolylines() {
        for (Line line : network.getLines()) {
            boolean isFirst = true;
            for (WorldPath path : line.getPaths()) {
                PolylineOptions options = new PolylineOptions().width(6).color(line.getColor()).geodesic(true);
                for (float[] point : path.getPath()) {
                    options.add(new LatLng(point[0], point[1]));
                }

                // add new points to the line for each station.
                // this ensures each station has a nearby polyline point, which is needed to show trains
                Map<LatLng, Stop> stopPerLoc = new HashMap<>();
                for (Stop stop : line.vertexSet()) {
                    float[] coords = stop.getStation().getWorldCoordinates();
                    LatLng pos = new LatLng(coords[0], coords[1]);
                    LatLng snappedPos = getMarkerProjectionOnSegment(pos, options.getPoints());
                    int idx = getLocationIndex(snappedPos, options.getPoints());
                    if (idx >= 0 && snappedPos != null) {
                        options.getPoints().add(idx + 1, snappedPos);
                        stopPerLoc.put(snappedPos, stop);
                    }
                }

                Polyline polyline = googleMap.addPolyline(options);
                if (isFirst) {
                    // register the indexes of each station point in the polyline
                    Map<Station, Integer> pointIdxForStation = new HashMap<>();
                    for (int i = 0; i < options.getPoints().size(); i++) {
                        LatLng point = options.getPoints().get(i);
                        if (stopPerLoc.containsKey(point)) {
                            pointIdxForStation.put(stopPerLoc.get(point).getStation(), i);
                        }
                    }
                    polylinePointIdxForStation.put(polyline, pointIdxForStation);

                    linePolylines.put(line, polyline);
                    isFirst = false;
                }
            }
        }
    }

    private void putMarkers(Collection<Station> stations, @Nullable LatLngBounds.Builder builder) {
        for (Marker m : stationMarkers.keySet()) {
            m.remove();
        }
        for (Marker m : exitMarkers.keySet()) {
            m.remove();
        }
        stationMarkers.clear();
        exitMarkers.clear();
        markerLinks.clear();
        for (Station station : stations) {
            LatLng pos = new LatLng(station.getWorldCoordinates()[0], station.getWorldCoordinates()[1]);
            if (builder != null) {
                builder.include(pos);
            }
            BitmapDescriptor icon;
            if (station.isAlwaysClosed()) {
                Drawable dotDrawable = ContextCompat.getDrawable(context, R.drawable.ic_station_dot);
                Drawable crossDrawable = ContextCompat.getDrawable(context, R.drawable.ic_close_white_24dp);

                int width = crossDrawable.getIntrinsicWidth();
                int height = crossDrawable.getIntrinsicHeight();
                float offset = width / 2f - dotDrawable.getIntrinsicWidth() / 2f;

                dotDrawable.setBounds(0, 0, dotDrawable.getIntrinsicWidth(), dotDrawable.getIntrinsicHeight());
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.translate(offset, offset);
                dotDrawable.draw(canvas);
                canvas.translate(-offset, -offset);

                crossDrawable.setBounds(0, 0, width, height);
                DrawableCompat.setTint(crossDrawable, ContextCompat.getColor(context, R.color.colorError));
                crossDrawable.draw(canvas);
                icon = BitmapDescriptorFactory.fromBitmap(bitmap);
            } else {
                icon = Util.getBitmapDescriptorFromVector(context, R.drawable.ic_station_dot);
            }

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .icon(icon));
            if (station.isExceptionallyClosed(network, new Date())) {
                marker.setAlpha(0.5f);
            }
            stationMarkers.put(marker, station);
            markerLinks.put(marker, "station:" + station.getId());

            // lobbies
            final int[] lobbyColors = Util.lobbyColors.clone();
            if (station.getLobbies().size() == 1) {
                lobbyColors[0] = Color.BLACK;
            }
            int curLobbyColorIdx = 0;
            for (Lobby lobby : station.getLobbies()) {
                for (Lobby.Exit exit : lobby.getExits()) {
                    pos = new LatLng(exit.worldCoord[0], exit.worldCoord[1]);
                    if (builder != null) {
                        builder.include(pos);
                    }
                    marker = googleMap.addMarker(new MarkerOptions()
                            .position(pos)
                            .icon(Util.createMapMarker(context,
                                    Util.getDrawableResourceIdForExitType(exit.type, lobby.isAlwaysClosed()), lobbyColors[curLobbyColorIdx]))
                            .alpha(lobby.isAlwaysClosed() ? 0.5f : 1));
                    marker.setVisible(false); // only shown when zoomed in past a certain level
                    exitMarkers.put(marker, lobby);
                    stationsOfExitMarkers.put(marker, station);
                    exitsOfExitMarkers.put(marker, exit);
                    markerLinks.put(marker, "station:" + station.getId() + ":lobby:" + lobby.getId() + ":exit:" + exit.id);
                }
                curLobbyColorIdx = (curLobbyColorIdx + 1) % lobbyColors.length;
            }
        }
    }

    private Marker placeTrainMarker(@Nullable Marker existing, API.MQTTvehiclePosition vehiclePos) {
        Station s1 = network.getStation(vehiclePos.prevStation);
        Station s2 = network.getStation(vehiclePos.nextStation);
        if (s1 == null || s2 == null) {
            return null;
        }

        List<LatLng> points = findPolylinePart(s1, s2);

        if (existing == null) {
            existing = googleMap.addMarker(new MarkerOptions()
                    .position(setMarkerPositionAndHeading(null, points, vehiclePos))
                    .zIndex(100)
                    .flat(true)
                    .visible(googleMap.getCameraPosition().zoom >= VEHICLE_MARKERS_ZOOM_THRESHOLD));
            iconFactory.setRotation(180);
            iconFactory.setContentRotation(90);
            iconFactory.setStyle(TrainIconGenerator.STYLE_ORANGE);
            if (vehiclePos.vehicle.matches(".*?[A-Z]$")) {
                Line line = network.getLineByExternalID(vehiclePos.vehicle.substring(vehiclePos.vehicle.length() - 1));
                if (line != null) {
                    iconFactory.setColor(ColorUtils.setAlphaComponent(line.getColor(), 200));
                }
            }
            existing.setIcon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(vehiclePos.vehicle)));
            existing.setAnchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());
        }
        setMarkerPositionAndHeading(existing, points, vehiclePos);
        return existing;
    }

    private Circle placeTrainCircle(@Nullable Circle existing, API.MQTTvehiclePosition vehiclePos) {
        Station s1 = network.getStation(vehiclePos.prevStation);
        Station s2 = network.getStation(vehiclePos.nextStation);
        if (s1 == null || s2 == null) {
            return null;
        }

        List<LatLng> points = findPolylinePart(s1, s2);

        if (existing == null) {
            existing = googleMap.addCircle(new CircleOptions()
                    .center(setMarkerPositionAndHeading(null, points, vehiclePos))
                    .radius(100)
                    .strokeWidth(0)
                    .zIndex(0)
                    .visible(googleMap.getCameraPosition().zoom < VEHICLE_MARKERS_ZOOM_THRESHOLD));
            if (vehiclePos.vehicle.matches(".*?[A-Z]$")) {
                Line line = network.getLineByExternalID(vehiclePos.vehicle.substring(vehiclePos.vehicle.length() - 1));
                if (line != null) {
                    existing.setFillColor(ColorUtils.setAlphaComponent(line.getColor(), 128));
                }
            }
        } else {
            existing.setCenter(setMarkerPositionAndHeading(null, points, vehiclePos));
        }
        return existing;
    }

    private List<LatLng> findPolylinePart(Station s1, Station s2) {
        // find a polyline that contains both stations
        Polyline polyline = null;
        int s1idx = -1, s2idx = -1;
        for (Map.Entry<Polyline, Map<Station, Integer>> entry : polylinePointIdxForStation.entrySet()) {
            if (entry.getValue().containsKey(s1) && entry.getValue().containsKey(s2)) {
                polyline = entry.getKey();
                s1idx = entry.getValue().get(s1);
                s2idx = entry.getValue().get(s2);
            }
        }
        if (polyline == null) {
            return null;
        }
        List<LatLng> points;
        if (s1idx > s2idx) {
            points = new ArrayList<>(polyline.getPoints().subList(s2idx, s1idx + 1));
            Collections.reverse(points);
        } else {
            points = polyline.getPoints().subList(s1idx, s2idx + 1);
        }
        return points;
    }

    private LatLng setMarkerPositionAndHeading(@Nullable Marker marker, List<LatLng> points, API.MQTTvehiclePosition vehiclePos) {
        double totalDistance = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            totalDistance += SphericalUtil.computeDistanceBetween(points.get(i), points.get(i + 1));
        }
        double targetDistance = (totalDistance * vehiclePos.percent) / 100d;

        LatLng firstPoint = null, secondPoint = null;
        double curDistance = 0;
        double segmentDistance = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            segmentDistance = SphericalUtil.computeDistanceBetween(points.get(i), points.get(i + 1));
            if (curDistance + segmentDistance > targetDistance) {
                firstPoint = points.get(i);
                secondPoint = points.get(i + 1);
                break;
            }
            curDistance += segmentDistance;
        }
        if (firstPoint == null || secondPoint == null) {
            firstPoint = points.get(points.size() - 1);
            secondPoint = points.get(points.size() - 1);
        }

        targetDistance = targetDistance - curDistance;
        // targetDistance is now relative to segmentDistance
        LatLng pos = SphericalUtil.interpolate(firstPoint, secondPoint, targetDistance / segmentDistance);
        if (marker != null) {
            if (firstPoint == secondPoint && points.size() >= 2) {
                // trick to get heading right when percentage == 100
                firstPoint = points.get(points.size() - 2);
            }
            double heading = SphericalUtil.computeHeading(firstPoint, secondPoint);
            marker.setPosition(pos);
            marker.setRotation((float) heading);
        }
        return pos;
    }

    @Override
    public void onVehiclePositionsReceived(List<API.MQTTvehiclePosition> positions) {
        uiThreadRunner.runOnUiThread(() -> {
            HashSet<String> seenVehicles = new HashSet<>();
            for (API.MQTTvehiclePosition position : positions) {
                seenVehicles.add(position.vehicle);

                Marker existing = vehicleMarkers.get(position.vehicle);
                existing = placeTrainMarker(existing, position);
                vehicleMarkers.put(position.vehicle, existing);

                Circle circle = vehicleCircles.get(position.vehicle);
                circle = placeTrainCircle(circle, position);
                vehicleCircles.put(position.vehicle, circle);
            }

            // remove gone vehicles
            Iterator<Map.Entry<String, Marker>> iter = vehicleMarkers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, Marker> entry = iter.next();
                if (!seenVehicles.contains(entry.getKey())) {
                    entry.getValue().remove();
                    iter.remove();
                }
            }

            Iterator<Map.Entry<String, Circle>> iterc = vehicleCircles.entrySet().iterator();
            while (iterc.hasNext()) {
                Map.Entry<String, Circle> entry = iterc.next();
                if (!seenVehicles.contains(entry.getKey())) {
                    entry.getValue().remove();
                    iterc.remove();
                }
            }
        });
    }

    private void checkShowTrains() {
        if (!mapReady) {
            return;
        }
        MqttManager mqttManager = Coordinator.get(context).getMqttManager();
        mqttManager.setOnVehiclePositionsReceivedListener(this);
        mqttPartyID = mqttManager.connect(mqttManager.getVehiclePositionsTopic());
    }

    @Override
    public void onResume() {
        if (mapView != null) {
            mapView.onResume();
            checkShowTrains();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        if (mqttPartyID >= 0) {
            final MqttManager mqttManager = Coordinator.get(context).getMqttManager();
            mqttManager.setOnVehiclePositionsReceivedListener(null);
            mqttManager.disconnect(mqttPartyID);
            mqttPartyID = -1;
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

    public static final String STATE_CAMERA_POSITION = "cameraPosition";

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (googleMap != null && mapReady) {
            outState.putParcelable(STATE_CAMERA_POSITION, googleMap.getCameraPosition());
        }
    }

    @Override
    public void onCameraIdle() {
        boolean stationMarkersVisible = !stationMarkers.isEmpty() && stationMarkers.keySet().iterator().next().isVisible();
        if (googleMap.getCameraPosition().zoom >= 15 && stationMarkersVisible) {
            for (Marker marker : stationMarkers.keySet()) {
                marker.setVisible(false);
            }
            for (Marker marker : exitMarkers.keySet()) {
                marker.setVisible(true);
            }
        } else if (googleMap.getCameraPosition().zoom < 15 && !stationMarkersVisible) {
            for (Marker marker : stationMarkers.keySet()) {
                marker.setVisible(true);
            }
            for (Marker marker : exitMarkers.keySet()) {
                marker.setVisible(false);
            }
        }

        boolean vehicleMarkersVisible = !vehicleMarkers.isEmpty() && vehicleMarkers.values().iterator().next().isVisible();
        if (googleMap.getCameraPosition().zoom >= VEHICLE_MARKERS_ZOOM_THRESHOLD && !vehicleMarkersVisible) {
            for (Marker marker : vehicleMarkers.values()) {
                marker.setVisible(true);
            }
            for (Circle circle : vehicleCircles.values()) {
                circle.setVisible(false);
            }
        } else if (googleMap.getCameraPosition().zoom < VEHICLE_MARKERS_ZOOM_THRESHOLD && vehicleMarkersVisible) {
            for (Marker marker : vehicleMarkers.values()) {
                marker.setVisible(false);
            }
            for (Circle circle : vehicleCircles.values()) {
                circle.setVisible(true);
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // This method returns a boolean that indicates whether you have consumed the event
        // (i.e., you want to suppress the default behavior).
        // we just want to disable clicks on train markers
        if (vehicleMarkers.values().contains(marker)) {
            return true;
        }
        // If it returns false, then the default behavior will occur in addition to your custom behavior.
        return false;
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        String link = markerLinks.get(marker);
        if (link == null) {
            return;
        }
        InternalLinkHandler.onClick(context, link, null);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        Station station = stationMarkers.get(marker);
        if (stationsOfExitMarkers.get(marker) != null) {
            station = stationsOfExitMarkers.get(marker);
        }
        if (station != null) {
            View view = layoutInflater.inflate(R.layout.infowindow_station, null, false);

            RouteFragment.populateStationView(context, station, view);

            LinearLayout stationIconsLayout = view.findViewById(R.id.station_icons_layout);

            List<Line> lines = new ArrayList<>(station.getLines());
            Collections.sort(lines, (l1, l2) -> Integer.valueOf(l1.getOrder()).compareTo(l2.getOrder()));

            for (Line l : lines) {
                Drawable drawable = ContextCompat.getDrawable(context, Util.getDrawableResourceIdForLineId(l.getId()));
                drawable.setColorFilter(l.getColor(), PorterDuff.Mode.SRC_ATOP);

                int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, context.getResources().getDisplayMetrics());
                FrameLayout iconFrame = new FrameLayout(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
                int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, context.getResources().getDisplayMetrics());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    params.setMarginEnd(margin);
                }
                int marginTop = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                params.setMargins(0, marginTop, margin, 0);
                iconFrame.setLayoutParams(params);
                iconFrame.setBackground(drawable);
                stationIconsLayout.addView(iconFrame);
            }

            TextView lobbyNameView = view.findViewById(R.id.lobby_name_view);
            TextView exitNameView = view.findViewById(R.id.exit_name_view);
            Lobby.Exit exit = exitsOfExitMarkers.get(marker);
            if (exit != null) {
                Lobby lobby = exitMarkers.get(marker);
                lobbyNameView.setVisibility(View.VISIBLE);
                lobbyNameView.setText(String.format(context.getString(R.string.frag_map_lobby_name), lobby.getName()));
                exitNameView.setText(exit.getExitsString());
            } else {
                exitNameView.setVisibility(View.GONE);
            }
            return view;
        }
        return null;
    }

    @Override
    public void setFilteredStations(Collection<Station> stations) {
        if (googleMap != null) {
            putMarkers(stations, null);
        }
    }

    @Override
    public void clearFilter() {
        putMarkers(network.getStations(), null);
    }
}