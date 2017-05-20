package im.tny.segvault.disturbances;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import io.realm.Realm;

public class StationActivity extends AppCompatActivity {

    private String networkId;
    private String stationId;

    MainService locService;
    boolean locBound = false;

    ImageView topImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            networkId = getIntent().getStringExtra(EXTRA_NETWORK_ID);
            stationId = getIntent().getStringExtra(EXTRA_STATION_ID);
        } else {
            networkId = savedInstanceState.getString(STATE_NETWORK_ID);
            stationId = savedInstanceState.getString(STATE_STATION_ID);
        }
        if (getLastNonConfigurationInstance() != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) getLastCustomNonConfigurationInstance();
            locService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            startService(new Intent(this, MainService.class));
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
        setContentView(R.layout.activity_station);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        topImage = (ImageView) findViewById(R.id.top_image);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        fab.hide();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private StationActivity.LocServiceConnection mConnection = new StationActivity.LocServiceConnection();

    class LocServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            locService = binder.getService();
            locBound = true;

            Network net = locService.getNetwork(networkId);
            Station station = net.getStation(stationId).get(0);

            setTitle(station.getName());
            getSupportActionBar().setTitle(station.getName());
            CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
            ctl.setTitle(station.getName());

            Set<Line> lineset = new HashSet<>(station.getLines());

            for (Connection c : station.getTransferEdges(net)) {
                lineset.addAll(c.getTarget().getLines());
            }
            List<Line> lines = new ArrayList<>(lineset);
            Collections.sort(lines, Collections.reverseOrder(new Comparator<Line>() {
                @Override
                public int compare(Line l1, Line l2) {
                    return l1.getName().compareTo(l2.getName());
                }
            }));

            if(lines.size() > 1) {
                int colors[] = new int[lines.size()*2];
                int i = 0;
                for (Line l : lines) {
                    colors[i++] = l.getColor();
                    colors[i++] = l.getColor();
                }

                GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
                gd.setCornerRadius(0f);
                ctl.setContentScrim(gd);

                gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
                gd.setCornerRadius(0f);
                ctl.setStatusBarScrim(gd);

                gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
                gd.setCornerRadius(0f);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    ctl.setBackgroundDrawable(gd);
                } else {
                    ctl.setBackground(gd);
                }
            } else {
                int color = lines.get(0).getColor();
                ctl.setContentScrimColor(color);
                ctl.setStatusBarScrimColor(color);
                ctl.setBackgroundColor(color);
            }

            // Connections
            TextView connectionsTitleView = (TextView) findViewById(R.id.connections_title_view);
            LinearLayout busLayout = (LinearLayout) findViewById(R.id.feature_bus_layout);
            if (station.getFeatures().bus) {
                busLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout boatLayout = (LinearLayout) findViewById(R.id.feature_boat_layout);
            if (station.getFeatures().boat) {
                boatLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout trainLayout = (LinearLayout) findViewById(R.id.feature_train_layout);
            if (station.getFeatures().train) {
                trainLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout airportLayout = (LinearLayout) findViewById(R.id.feature_airport_layout);
            if (station.getFeatures().airport) {
                airportLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            // Accessibility
            TextView accessibilityTitleView = (TextView) findViewById(R.id.accessibility_title_view);
            LinearLayout liftLayout = (LinearLayout) findViewById(R.id.feature_lift_layout);
            if (station.getFeatures().lift) {
                liftLayout.setVisibility(View.VISIBLE);
                accessibilityTitleView.setVisibility(View.VISIBLE);
            }

            // Statistics
            Realm realm = Realm.getDefaultInstance();
            TextView statsEntryCountView = (TextView) findViewById(R.id.station_entry_count_view);
            long entryCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_ENTRY.name()).count();
            statsEntryCountView.setText(String.format(getString(R.string.frag_station_stats_entry), entryCount));

            TextView statsExitCountView = (TextView) findViewById(R.id.station_exit_count_view);
            long exitCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_EXIT.name()).count();
            statsExitCountView.setText(String.format(getString(R.string.frag_station_stats_exit), exitCount));

            TextView statsGoneThroughCountView = (TextView) findViewById(R.id.station_gone_through_count_view);
            long goneThroughCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.GONE_THROUGH.name()).count();
            statsGoneThroughCountView.setText(String.format(getString(R.string.frag_station_stats_gone_through), goneThroughCount));

            TextView statsTransferCountView = (TextView) findViewById(R.id.station_transfer_count_view);
            if(station.hasTransferEdge(net)) {
                long transferCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.INTERCHANGE.name()).count();
                statsTransferCountView.setText(String.format(getString(R.string.frag_station_stats_transfer), transferCount));
                statsTransferCountView.setVisibility(View.VISIBLE);
            } else {
                statsTransferCountView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locBound = false;
        }

        public MainService.LocalBinder getBinder() {
            return binder;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final String STATE_STATION_ID = "stationId";
    public static final String STATE_NETWORK_ID = "networkId";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_STATION_ID, stationId);
        savedInstanceState.putString(STATE_NETWORK_ID, networkId);


        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_STATION_ID = "im.tny.segvault.disturbances.extra.StationActivity.stationid";
    public static final String EXTRA_NETWORK_ID = "im.tny.segvault.disturbances.extra.StationActivity.networkid";

}
