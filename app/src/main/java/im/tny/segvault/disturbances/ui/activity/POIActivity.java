package im.tny.segvault.disturbances.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import net.opacapp.multilinecollapsingtoolbar.CollapsingToolbarLayout;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.disturbances.ui.util.ScrollFixMapView;
import im.tny.segvault.subway.POI;
import im.tny.segvault.subway.Station;

public class POIActivity extends TopActivity {

    private String poiId;

    private ScrollFixMapView mapView;
    private GoogleMap googleMap;
    private boolean mapLayoutReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            poiId = getIntent().getStringExtra(EXTRA_POI_ID);
        } else {
            poiId = savedInstanceState.getString(STATE_POI_ID);
        }

        setContentView(R.layout.activity_poi);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.hide();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mapView = (ScrollFixMapView) findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        try {
            MapsInitializer.initialize(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mapView.onResume(); // needed to get the map to display immediately

        final POI poi = Coordinator.get(this).getMapManager().getPOI(poiId);

        if (poi == null) {
            finish();
        }

        final String[] names = poi.getNames(Util.getCurrentLanguage(POIActivity.this));
        setTitle(names[0]);
        getSupportActionBar().setTitle(names[0]);
        AppBarLayout abl = (AppBarLayout) findViewById(R.id.app_bar);
        final CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        ctl.setTitle(names[0]);

        if (names.length > 1) {
            getSupportActionBar().setSubtitle(names[1]);
            final TextView subtitle = (TextView) findViewById(R.id.action_bar_subtitle);
            subtitle.setText(names[1]);
            ctl.setExpandedTitleMarginBottom(subtitle.getHeight() * 3);

            if (names[0].length() > 70 || names[1].length() > 70) {
                findViewById(R.id.top_image).setLayoutParams(
                        new CollapsingToolbarLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 210, getResources().getDisplayMetrics())));
            }

            ctl.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        ctl.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        ctl.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                    ctl.setExpandedTitleMarginBottom(subtitle.getHeight() + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
                }
            });
        }

        int color = Util.getColorForPOIType(poi.getType(), POIActivity.this);
        ctl.setContentScrimColor(color);
        ctl.setStatusBarScrimColor(color);
        abl.setBackgroundColor(color);

        // POI icon on top
        Drawable drawable = ContextCompat.getDrawable(POIActivity.this, Util.getDrawableResourceIdForPOIType(poi.getType()));
        int newColor = Util.manipulateColor(color, 1.2f);
        drawable.setColorFilter(newColor, PorterDuff.Mode.SRC_ATOP);

        int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160, getResources().getDisplayMetrics());
        final FrameLayout iconFrame = new FrameLayout(POIActivity.this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
        iconFrame.setLayoutParams(params);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            iconFrame.setBackgroundDrawable(drawable);
        } else {
            iconFrame.setBackground(drawable);
        }
        final LinearLayout iconLayout = (LinearLayout) findViewById(R.id.icon_layout);
        iconLayout.addView(iconFrame);

        abl.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (ctl.getHeight() + verticalOffset < 2.5 * ViewCompat.getMinimumHeight(ctl)) {
                    iconLayout.animate().alpha(0);
                } else {
                    iconLayout.animate().alpha(1);
                }
            }
        });
        // end of POI icon

        if (!poi.getWebURL().isEmpty()) {
            Button webpageButton = (Button) findViewById(R.id.webpage_button);
            webpageButton.setVisibility(View.VISIBLE);
            webpageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(poi.getWebURL()));
                    try {
                        startActivity(browserIntent);
                    } catch (ActivityNotFoundException e) {
                        // oh well
                    }
                }
            });
        }

        Button mapButton = (Button) findViewById(R.id.map_button);
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse(String.format(
                                "https://www.google.com/maps/search/?api=1&query=%f,%f",
                                poi.getWorldCoord()[0], poi.getWorldCoord()[1])));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // oh well
                }
            }
        });

        LinearLayout stationsLayout = (LinearLayout) findViewById(R.id.stations_layout);
        for (final Station station : Coordinator.get(this).getMapManager().getAllStations()) {
            if (station.getPOIs().contains(poi)) {
                View stepview = getLayoutInflater().inflate(R.layout.path_station, stationsLayout, false);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                timeView.setVisibility(View.INVISIBLE);

                FrameLayout prevLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setVisibility(View.GONE);
                FrameLayout nextLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setVisibility(View.GONE);

                FrameLayout leftLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.left_line_stripe_layout);
                leftLineStripeLayout.setVisibility(View.VISIBLE);
                FrameLayout rightLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.right_line_stripe_layout);
                rightLineStripeLayout.setVisibility(View.VISIBLE);

                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.RIGHT_LEFT,
                        new int[]{0, station.getLines().get(0).getColor()});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    rightLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    rightLineStripeLayout.setBackground(gd);
                }

                gd = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0, station.getLines().get(station.getLines().size() > 1 ? 1 : 0).getColor()});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    leftLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    leftLineStripeLayout.setBackground(gd);
                }

                ImageView crossView = (ImageView) stepview.findViewById(R.id.station_cross_image);
                if (station.isAlwaysClosed()) {
                    crossView.setVisibility(View.VISIBLE);
                }

                RouteFragment.populateStationView(POIActivity.this, station, stepview, true, false);

                stepview.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(POIActivity.this, StationActivity.class);
                        intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                        intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                        startActivity(intent);
                    }
                });

                stationsLayout.addView(stepview);
            }
        }

        final ViewTreeObserver vto = mapView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    mapLayoutReady = true;
                    trySetupMap(poi, names[0]);
                    // remove the listener... or we'll be doing this a lot.
                    ViewTreeObserver obs = mapView.getViewTreeObserver();
                    obs.removeGlobalOnLayoutListener(this);
                }
            });
        }

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap mMap) {
                googleMap = mMap;

                trySetupMap(poi, names[0]);
            }
        });
    }

    private void trySetupMap(final POI poi, final String name) {
        if (googleMap == null || !mapLayoutReady) {
            return;
        }

        LatLng pos = new LatLng(poi.getWorldCoord()[0], poi.getWorldCoord()[1]);
        googleMap.addMarker(new MarkerOptions().position(pos).title(name));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(pos.latitude, pos.longitude), 15.0f));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final String STATE_POI_ID = "poiId";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_POI_ID, poiId);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_POI_ID = "im.tny.segvault.disturbances.extra.POIActivity.poiid";

}
