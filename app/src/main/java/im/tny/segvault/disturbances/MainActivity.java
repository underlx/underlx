package im.tny.segvault.disturbances;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        HomeFragment.OnFragmentInteractionListener,
        AboutFragment.OnFragmentInteractionListener,
        LineFragment.OnListFragmentInteractionListener {

    LocationService locService;
    boolean locBound = false;

    NavigationView navigationView;

    LocalBroadcastManager bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (locBound) {
                    TextView t = (TextView) findViewById(R.id.debug_info);
                    t.setText(locService.dumpDebugInfo());
                }
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // show home fragment
        if (savedInstanceState == null) {
            Fragment newFragment = HomeFragment.newInstance();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_container, newFragment);
            transaction.commit();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationService.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(LocationService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }

    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 10001;

    @Override
    protected void onStart() {
        super.onStart();
        startService(new Intent(this, LocationService.class));
        // Bind to LocalService
        bindService(new Intent(this, LocationService.class), mConnection, Context.BIND_AUTO_CREATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (locBound) {
            unbindService(mConnection);
            locBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        bm.unregisterReceiver(mBroadcastReceiver);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        Class fragmentClass = null;
        switch (id) {
            case R.id.nav_home:
                fragmentClass = HomeFragment.class;
                break;
            case R.id.nav_about:
                fragmentClass = AboutFragment.class;
                break;
            default:
                Snackbar.make(findViewById(R.id.fab), R.string.status_not_yet_implemented, Snackbar.LENGTH_LONG).show();
                break;
        }

        if (fragmentClass != null) {
            // Create new fragment and transaction
            Fragment newFragment = null;
            try {
                newFragment = (Fragment) fragmentClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack
            transaction.replace(R.id.main_fragment_container, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locService = binder.getService();
            locBound = true;
            Intent intent = new Intent(ACTION_LOCATION_SERVICE_BOUND);
            bm.sendBroadcast(intent);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locBound = false;
        }
    };

    private Snackbar topologyUpdateSnackbar = null;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(LocationService.ACTION_UPDATE_TOPOLOGY_PROGRESS)) {
                final int progress = intent.getIntExtra(LocationService.EXTRA_UPDATE_TOPOLOGY_PROGRESS, 0);
                final String msg = String.format(getString(R.string.update_topology_progress), progress);
                if (topologyUpdateSnackbar == null) {
                    topologyUpdateSnackbar = Snackbar.make(findViewById(R.id.fab), msg, Snackbar.LENGTH_LONG)
                            .setAction(R.string.update_topology_cancel_action, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (locBound) {
                                        locService.cancelTopologyUpdate();
                                    }
                                }
                            });
                    topologyUpdateSnackbar.show();
                } else {
                    topologyUpdateSnackbar.setText(msg);
                    topologyUpdateSnackbar.show();
                }
            } else if (intent.getAction().equals(LocationService.ACTION_UPDATE_TOPOLOGY_FINISHED)) {
                final boolean success = intent.getBooleanExtra(LocationService.EXTRA_UPDATE_TOPOLOGY_FINISHED, false);
                if (topologyUpdateSnackbar != null) {
                    if (success) {
                        topologyUpdateSnackbar.setAction("", null);
                        topologyUpdateSnackbar.setText(R.string.update_topology_success);
                        topologyUpdateSnackbar.show();
                    } else {
                        topologyUpdateSnackbar.setAction("", null);
                        topologyUpdateSnackbar.setText(R.string.update_topology_failure);
                        topologyUpdateSnackbar.show();
                    }
                    topologyUpdateSnackbar = null;
                }
            }
        }
    };

    @Override
    public void setActionBarTitle(String title) {
        setTitle(title);
    }

    @Override
    public void checkNavigationDrawerItem(int id) {
        MenuItem item = navigationView.getMenu().findItem(id);
        if (item != null) {
            item.setChecked(true);
        }
    }

    @Override
    public Collection<Network> getNetworks() {
        return locService.getNetworks();
    }

    @Override
    public void updateNetworks(String... network_ids) {
        if (locBound) {
            locService.updateTopology(network_ids);
        }
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onListFragmentInteraction(MyLineRecyclerViewAdapter.LineItem item) {

    }

    @Override
    public LocationService getLocationService() {
        return locService;
    }

    public static final String ACTION_LOCATION_SERVICE_BOUND = "im.tny.segvault.disturbances.action.MainActivity.locservicebound";
}
