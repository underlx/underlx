package im.tny.segvault.disturbances;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;

import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        HomeFragment.OnFragmentInteractionListener,
        RouteFragment.OnFragmentInteractionListener,
        MapFragment.OnFragmentInteractionListener,
        HelpFragment.OnFragmentInteractionListener,
        AboutFragment.OnFragmentInteractionListener,
        LineFragment.OnListFragmentInteractionListener,
        AnnouncementFragment.OnListFragmentInteractionListener,
        DisturbanceFragment.OnListFragmentInteractionListener,
        NotifPreferenceFragment.OnFragmentInteractionListener,
        GeneralPreferenceFragment.OnFragmentInteractionListener,
        TripHistoryFragment.OnListFragmentInteractionListener,
        TripFragment.OnFragmentInteractionListener,
        UnconfirmedTripsFragment.OnListFragmentInteractionListener {

    MainService locService;
    boolean locBound = false;

    NavigationView navigationView;

    LocalBroadcastManager bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Object conn = getLastCustomNonConfigurationInstance();
        if (conn != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) conn;
            locService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            startService(new Intent(this, MainService.class));
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if (savedInstanceState == null) {
            // show initial fragment
            Fragment newFragment = null;
            if (getIntent() != null) {
                newFragment = getNewFragment(getIntent().getIntExtra(EXTRA_INITIAL_FRAGMENT, R.id.nav_home));
            }
            if (newFragment == null) {
                newFragment = new HomeFragment();
            }
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_container, newFragment);
            transaction.commit();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_TOPOLOGY_UPDATE_AVAILABLE);
        filter.addAction(MainService.ACTION_CACHE_EXTRAS_PROGRESS);
        filter.addAction(MainService.ACTION_CACHE_EXTRAS_FINISHED);
        bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
                boolean isFirstStart = sharedPref.getBoolean("fuse_first_run", true);

                if (isFirstStart) {
                    // intro will request permission for us
                    final Intent i = new Intent(MainActivity.this, IntroActivity.class);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(i);
                        }
                    });
                } else {
                    boolean locationEnabled = sharedPref.getBoolean("pref_location_enable", true);
                    if (locationEnabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
                                }
                            }
                        });

                    }
                }
            }
        });

        // Start the thread
        t.start();
    }

    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 10001;

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        bm.unregisterReceiver(mBroadcastReceiver);

        // Unbind from the service
        if (locBound && isFinishing()) {
            getApplicationContext().unbindService(mConnection);
            locBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private Fragment getNewFragment(int id) {
        try {
            switch (id) {
                case R.id.nav_home:
                    return HomeFragment.newInstance();
                case R.id.nav_plan_route:
                    return RouteFragment.newInstance(MainService.PRIMARY_NETWORK_ID);
                case R.id.nav_trip_history:
                    return TripHistoryFragment.newInstance(1);
                case R.id.nav_map:
                    return MapFragment.newInstance(MainService.PRIMARY_NETWORK_ID);
                case R.id.nav_announcements:
                    return AnnouncementFragment.newInstance(MainService.PRIMARY_NETWORK_ID, 1);
                case R.id.nav_disturbances:
                    return DisturbanceFragment.newInstance(1);
                case R.id.nav_notif:
                    return NotifPreferenceFragment.newInstance();
                case R.id.nav_settings:
                    return GeneralPreferenceFragment.newInstance();
                case R.id.nav_help:
                    return HelpFragment.newInstance();
                case R.id.nav_about:
                    return AboutFragment.newInstance();
                default:
                    return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Action bar menu item selected
        Fragment newFragment = getNewFragment(item.getItemId());

        if (newFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack
            transaction.replace(R.id.main_fragment_container, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        Fragment newFragment = getNewFragment(item.getItemId());

        if (newFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack
            transaction.replace(R.id.main_fragment_container, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } else {
            Snackbar.make(findViewById(R.id.fab), R.string.status_not_yet_implemented, Snackbar.LENGTH_LONG).show();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onHelpLinkClicked(String destination) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_fragment_container, HelpFragment.newInstance(destination));
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onStationLinkClicked(String destination) {
        if (locService != null) {
            for (Network network : locService.getNetworks()) {
                Station station;
                if ((station = network.getStation(destination)) != null) {
                    Intent intent = new Intent(this, StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, network.getId());
                    startActivity(intent);
                    return;
                }
            }
        }
    }

    @Override
    public void onMailtoLinkClicked(String address) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{address});
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onLinkClicked(String destination) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(destination));
        if (browserIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(browserIntent);
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private LocServiceConnection mConnection = new LocServiceConnection();

    class LocServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            locService = binder.getService();
            locBound = true;
            Intent intent = new Intent(ACTION_MAIN_SERVICE_BOUND);
            bm.sendBroadcast(intent);
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
    public Object onRetainCustomNonConfigurationInstance() {
        // have the service connection survive through activity configuration changes
        // (e.g. screen orientation changes)
        return mConnection;
    }

    private Snackbar topologyUpdateSnackbar = null;
    private Snackbar cacheExtrasSnackbar = null;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS:
                    final int progress = intent.getIntExtra(MainService.EXTRA_UPDATE_TOPOLOGY_PROGRESS, 0);
                    final String msg = String.format(getString(R.string.update_topology_progress), progress);
                    if (topologyUpdateSnackbar == null) {
                        topologyUpdateSnackbar = Snackbar.make(findViewById(R.id.fab), msg, Snackbar.LENGTH_INDEFINITE)
                                .setAction(R.string.update_topology_cancel_action, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if (locBound) {
                                            locService.cancelTopologyUpdate();
                                        }
                                    }
                                });
                    } else {
                        topologyUpdateSnackbar.setText(msg);
                    }
                    if (!topologyUpdateSnackbar.isShown()) {
                        topologyUpdateSnackbar.show();
                    }
                    break;
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    final boolean success = intent.getBooleanExtra(MainService.EXTRA_UPDATE_TOPOLOGY_FINISHED, false);
                    if (topologyUpdateSnackbar != null) {
                        topologyUpdateSnackbar.setAction("", null);
                        topologyUpdateSnackbar.setDuration(Snackbar.LENGTH_LONG);
                        if (success) {
                            topologyUpdateSnackbar.setText(R.string.update_topology_success);
                        } else {
                            topologyUpdateSnackbar.setText(R.string.update_topology_failure);
                        }
                        topologyUpdateSnackbar.show();
                        topologyUpdateSnackbar = null;
                    }
                    break;
                case MainService.ACTION_TOPOLOGY_UPDATE_AVAILABLE:
                    Snackbar.make(findViewById(R.id.fab), R.string.update_topology_available, 10000)
                            .setAction(R.string.update_topology_update_action, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (locBound) {
                                        locService.updateTopology();
                                    }
                                }
                            }).show();
                    break;
                case MainService.ACTION_CACHE_EXTRAS_PROGRESS:
                    final int progressCurrent = intent.getIntExtra(MainService.EXTRA_CACHE_EXTRAS_PROGRESS_CURRENT, 0);
                    final int progressTotal = intent.getIntExtra(MainService.EXTRA_CACHE_EXTRAS_PROGRESS_TOTAL, 1);
                    final String msg2 = String.format(getString(R.string.cache_extras_progress), (progressCurrent * 100) / progressTotal);
                    if (cacheExtrasSnackbar == null) {
                        cacheExtrasSnackbar = Snackbar.make(findViewById(R.id.fab), msg2, Snackbar.LENGTH_INDEFINITE);
                    } else {
                        cacheExtrasSnackbar.setText(msg2);
                        if (!cacheExtrasSnackbar.isShown()) {
                            cacheExtrasSnackbar.show();
                        }
                    }
                    break;
                case MainService.ACTION_CACHE_EXTRAS_FINISHED:
                    final boolean success2 = intent.getBooleanExtra(MainService.EXTRA_CACHE_EXTRAS_FINISHED, false);
                    if (cacheExtrasSnackbar != null) {
                        cacheExtrasSnackbar.setDuration(Snackbar.LENGTH_LONG);
                        if (success2) {
                            cacheExtrasSnackbar.setText(R.string.cache_extras_success);
                        } else {
                            cacheExtrasSnackbar.setText(R.string.cache_extras_failure);
                        }
                        cacheExtrasSnackbar.show();
                        cacheExtrasSnackbar = null;
                    }
                    break;
            }
        }
    };

    @Override
    public void checkNavigationDrawerItem(int id) {
        MenuItem item = navigationView.getMenu().findItem(id);
        if (item != null) {
            item.setChecked(true);
        } else {
            int size = navigationView.getMenu().size();
            for (int i = 0; i < size; i++) {
                navigationView.getMenu().getItem(i).setChecked(false);
            }
        }
    }

    @Override
    public Collection<Network> getNetworks() {
        if (locBound) {
            return locService.getNetworks();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public void updateNetworks(String... network_ids) {
        if (locBound) {
            locService.updateTopology(network_ids);
        }
    }

    @Override
    public void cacheAllExtras(String... network_ids) {
        if (locBound) {
            locService.cacheAllExtras(network_ids);
        }
    }

    @Override
    public void onListFragmentInteraction(LineRecyclerViewAdapter.LineItem item) {
        if (locService != null) {
            for (Network network : locService.getNetworks()) {
                Line line;
                if ((line = network.getLine(item.id)) != null) {
                    Intent intent = new Intent(this, LineActivity.class);
                    intent.putExtra(LineActivity.EXTRA_LINE_ID, line.getId());
                    intent.putExtra(LineActivity.EXTRA_NETWORK_ID, network.getId());
                    startActivity(intent);
                    return;
                }
            }
        }
    }

    @Override
    public void onFinishedRefreshing() {
        SwipeRefreshLayout srl = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        srl.setRefreshing(false);
    }

    @Override
    public void onListFragmentInteraction(DisturbanceRecyclerViewAdapter.DisturbanceItem item) {

    }

    @Override
    public void onListFragmentClick(TripRecyclerViewAdapter.TripItem item) {
        TripFragment f = TripFragment.newInstance(item.networkId, item.id);
        f.show(getSupportFragmentManager(), "trip-fragment");
    }

    @Override
    public void onListFragmentConfirmButtonClick(TripRecyclerViewAdapter.TripItem item) {
        Trip.confirm(item.id);
    }

    @Override
    public void onListFragmentCorrectButtonClick(TripRecyclerViewAdapter.TripItem item) {
        Intent intent = new Intent(this, TripCorrectionActivity.class);
        intent.putExtra(TripCorrectionActivity.EXTRA_NETWORK_ID, item.networkId);
        intent.putExtra(TripCorrectionActivity.EXTRA_TRIP_ID, item.id);
        startActivity(intent);
    }

    @Override
    public void onListFragmentInteraction(AnnouncementRecyclerViewAdapter.AnnouncementItem item) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
        startActivity(browserIntent);
    }


    @Override
    public MainService getMainService() {
        return locService;
    }

    @Override
    public LineStatusCache getLineStatusCache() {
        if (locService == null) {
            return null;
        }
        return locService.getLineStatusCache();
    }

    public static final String ACTION_MAIN_SERVICE_BOUND = "im.tny.segvault.disturbances.action.MainActivity.mainservicebound";

    public static final String EXTRA_INITIAL_FRAGMENT = "im.tny.segvault.disturbances.extra.MainActivity.initialfragment";
}
