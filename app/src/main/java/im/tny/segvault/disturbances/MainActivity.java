package im.tny.segvault.disturbances;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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

import im.tny.segvault.subway.Network;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        HomeFragment.OnFragmentInteractionListener,
        RouteFragment.OnFragmentInteractionListener,
        MapFragment.OnFragmentInteractionListener,
        AboutFragment.OnFragmentInteractionListener,
        LineFragment.OnListFragmentInteractionListener,
        AnnouncementFragment.OnListFragmentInteractionListener,
        DisturbanceFragment.OnListFragmentInteractionListener,
        NotifPreferenceFragment.OnFragmentInteractionListener,
        GeneralPreferenceFragment.OnFragmentInteractionListener,
        TripHistoryFragment.OnListFragmentInteractionListener,
        TripFragment.OnFragmentInteractionListener {

    MainService locService;
    boolean locBound = false;

    NavigationView navigationView;

    LocalBroadcastManager bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getLastCustomNonConfigurationInstance() != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) getLastCustomNonConfigurationInstance();
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
        bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }

    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 10001;

    @Override
    protected void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
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
                case R.id.nav_about:
                    return AboutFragment.newInstance();
                case R.id.nav_announcements:
                    return AnnouncementFragment.newInstance(MainService.PRIMARY_NETWORK_ID, 1);
                case R.id.nav_disturbances:
                    return DisturbanceFragment.newInstance(1);
                case R.id.nav_notif:
                    return NotifPreferenceFragment.newInstance();
                case R.id.nav_settings:
                    return GeneralPreferenceFragment.newInstance();
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
                        if (!topologyUpdateSnackbar.isShown()) {
                            topologyUpdateSnackbar.show();
                        }
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
    public void onListFragmentInteraction(LineRecyclerViewAdapter.LineItem item) {

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
    public void onListFragmentInteraction(TripRecyclerViewAdapter.TripItem item) {
        if (item.isTrip) {
            Intent intent = new Intent(this, StationActivity.class);
            intent.putExtra(StationActivity.EXTRA_STATION_ID, item.originId);
            intent.putExtra(StationActivity.EXTRA_NETWORK_ID, item.networkId);
            startActivity(intent);
        } else {
            TripFragment f = TripFragment.newInstance(item.networkId, item.id);
            f.show(getSupportFragmentManager(), "trip-fragment");
        }
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
        if(locService == null) {
            return null;
        }
        return locService.getLineStatusCache();
    }

    public static final String ACTION_MAIN_SERVICE_BOUND = "im.tny.segvault.disturbances.action.MainActivity.mainservicebound";

    public static final String EXTRA_INITIAL_FRAGMENT = "im.tny.segvault.disturbances.extra.MainActivity.initialfragment";
}
