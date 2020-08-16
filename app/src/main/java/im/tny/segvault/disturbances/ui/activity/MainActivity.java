package im.tny.segvault.disturbances.ui.activity;

import android.Manifest;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.FeedbackUtil;
import im.tny.segvault.disturbances.InternalLinkHandler;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.ui.adapter.AnnouncementRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.adapter.DisturbanceRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.adapter.LineRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.adapter.TripRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.fragment.HomeBackersFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeFavoriteStationsFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeLinesFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeStatsFragment;
import im.tny.segvault.disturbances.ui.fragment.HtmlDialogFragment;
import im.tny.segvault.disturbances.ui.fragment.MainAddableFragment;
import im.tny.segvault.disturbances.ui.fragment.TripFragment;
import im.tny.segvault.disturbances.ui.fragment.UnconfirmedTripsFragment;
import im.tny.segvault.disturbances.ui.fragment.top.AboutFragment;
import im.tny.segvault.disturbances.ui.fragment.top.AnnouncementFragment;
import im.tny.segvault.disturbances.ui.fragment.top.DisturbanceFragment;
import im.tny.segvault.disturbances.ui.fragment.top.ErrorFragment;
import im.tny.segvault.disturbances.ui.fragment.top.GeneralPreferenceFragment;
import im.tny.segvault.disturbances.ui.fragment.top.HelpFragment;
import im.tny.segvault.disturbances.ui.fragment.top.HomeFragment;
import im.tny.segvault.disturbances.ui.fragment.top.InfraFragment;
import im.tny.segvault.disturbances.ui.fragment.top.MapFragment;
import im.tny.segvault.disturbances.ui.fragment.top.NotifPreferenceFragment;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.disturbances.ui.fragment.top.TripHistoryFragment;
import im.tny.segvault.disturbances.ui.intro.IntroActivity;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

public class MainActivity extends TopActivity
        implements SearchView.OnSuggestionListener,
        NavigationView.OnNavigationItemSelectedListener,
        HomeFragment.OnFragmentInteractionListener,
        RouteFragment.OnFragmentInteractionListener,
        MapFragment.OnFragmentInteractionListener,
        HelpFragment.OnFragmentInteractionListener,
        InfraFragment.OnFragmentInteractionListener,
        AboutFragment.OnFragmentInteractionListener,
        HomeLinesFragment.OnListFragmentInteractionListener,
        HomeStatsFragment.OnFragmentInteractionListener,
        HomeBackersFragment.OnFragmentInteractionListener,
        HomeFavoriteStationsFragment.OnListFragmentInteractionListener,
        AnnouncementFragment.OnListFragmentInteractionListener,
        DisturbanceFragment.OnListFragmentInteractionListener,
        NotifPreferenceFragment.OnFragmentInteractionListener,
        GeneralPreferenceFragment.OnFragmentInteractionListener,
        TripHistoryFragment.OnListFragmentInteractionListener,
        TripFragment.OnFragmentInteractionListener,
        ErrorFragment.OnFragmentInteractionListener,
        UnconfirmedTripsFragment.OnListFragmentInteractionListener,
        SearchView.OnQueryTextListener {

    private MainService locService;
    private boolean locBound = false;

    private NavigationView navigationView;
    private MenuItem searchItem;
    private SearchView searchView;

    private LocalBroadcastManager bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Object conn = getLastCustomNonConfigurationInstance();
        if (conn != null && ((LocServiceConnection) conn).getBinder() != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) conn;
            locService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Log.d("Locale", "Initialized locale " + getString(R.string.frag_about_thanks_title));

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        LinearLayout headerLayout = navigationView.getHeaderView(0).findViewById(R.id.nav_header_layout);
        headerLayout.setBackgroundResource(Coordinator.get(this).getNavImageResource());
        TextView navTitle = navigationView.getHeaderView(0).findViewById(R.id.nav_title_view);
        navTitle.setShadowLayer(7, 0, 0, Coordinator.get(this).getNavTextShadowColor());

        Fragment newFragment = null;
        if (getIntent() != null) {
            String id = getIntent().getStringExtra(EXTRA_INITIAL_FRAGMENT);
            if (id != null) {
                newFragment = getNewFragment(pageStringToResourceId(id));
                planRouteTo = getIntent().getStringExtra(EXTRA_PLAN_ROUTE_TO_STATION);
            }

            if (getIntent().getBooleanExtra(EXTRA_FROM_INTRO, false)) {
                showTargetPrompt();
            }
        }

        if (savedInstanceState == null) {
            // show initial fragment
            if (newFragment == null) {
                newFragment = HomeFragment.newInstance(MapManager.PRIMARY_NETWORK_ID);
            }
            replaceFragment(newFragment, false);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MapManager.ACTION_TOPOLOGY_UPDATE_AVAILABLE);
        filter.addAction(Coordinator.ACTION_CACHE_EXTRAS_PROGRESS);
        filter.addAction(Coordinator.ACTION_CACHE_EXTRAS_FINISHED);
        filter.addAction(FeedbackUtil.ACTION_FEEDBACK_PROVIDED);
        filter.addAction(ReportActivity.ACTION_REPORT_PROVIDED);
        filter.addAction(ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(API.ACTION_ENDPOINT_META_AVAILABLE);
        bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        Thread t = new Thread(() -> {
            SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
            boolean isFirstStart = sharedPref.getBoolean("fuse_first_run", true);

            if (isFirstStart) {
                // intro will request permission for us
                final Intent i = new Intent(MainActivity.this, IntroActivity.class);
                runOnUiThread(() -> {
                    startActivity(i);
                    finish();
                });
            } else {
                boolean locationEnabled = sharedPref.getBoolean(PreferenceNames.LocationEnable, true);
                if (locationEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
                        }
                    });

                }
            }
        });

        // Start the thread
        t.start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            String id = intent.getStringExtra(EXTRA_INITIAL_FRAGMENT);
            if (id != null) {
                Fragment newFragment = getNewFragment(pageStringToResourceId(id));
                planRouteTo = intent.getStringExtra(EXTRA_PLAN_ROUTE_TO_STATION);
                replaceFragment(newFragment, true);
            }

            if (intent.getBooleanExtra(EXTRA_FROM_INTRO, false)) {
                showTargetPrompt();
            }
        }

    }

    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 10001;

    private void showTargetPrompt() {
        new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(Util.getToolbarNavigationIcon(findViewById(R.id.toolbar)))
                .setPrimaryText(R.string.act_main_nav_taptarget_title)
                .setSecondaryText(R.string.act_main_nav_taptarget_subtitle)
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        // User has pressed the prompt target
                    }
                })
                .setFocalColour(ContextCompat.getColor(this, R.color.colorAccent))
                .setBackgroundColour(ContextCompat.getColor(this, R.color.colorPrimaryLight))
                .show();
    }

    private boolean isVisible = false;

    @Override
    protected void onStart() {
        super.onStart();
        isVisible = true;
    }

    @Override
    protected void onStop() {
        isVisible = false;
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
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        boolean devMode = sharedPref.getBoolean(PreferenceNames.DeveloperMode, false);
        if (!devMode) {
            menu.findItem(R.id.menu_debug).setVisible(false);
        }

        searchItem = menu.findItem(R.id.search);
        searchView = (SearchView) searchItem.getActionView();
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnSuggestionListener(this);
        searchView.setOnQueryTextListener(this);
        // Detect SearchView open / close
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                setItemsVisibility(menu, searchItem, false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                invalidateOptionsMenu();
                return true;
            }
        });

        return true;
    }

    private void setItemsVisibility(Menu menu, MenuItem exception, boolean visible) {
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (item != exception) item.setVisible(visible);
        }
    }

    private Fragment getNewFragment(int id) {
        try {
            switch (id) {
                case R.id.nav_home:
                    return HomeFragment.newInstance(MapManager.PRIMARY_NETWORK_ID);
                case R.id.nav_plan_route:
                    return RouteFragment.newInstance(MapManager.PRIMARY_NETWORK_ID);
                case R.id.nav_trip_history:
                    return TripHistoryFragment.newInstance(1);
                case R.id.nav_map:
                    return MapFragment.newInstance(MapManager.PRIMARY_NETWORK_ID);
                case R.id.nav_announcements:
                    return AnnouncementFragment.newInstance();
                case R.id.nav_disturbances:
                    return DisturbanceFragment.newInstance(1);
                case R.id.nav_infrastructure:
                    return InfraFragment.newInstance();
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

    private static int pageStringToResourceId(String id) {
        switch (id) {
            case "nav_home":
                return R.id.nav_home;
            case "nav_plan_route":
                return R.id.nav_plan_route;
            case "nav_trip_history":
                return R.id.nav_trip_history;
            case "nav_map":
                return R.id.nav_map;
            case "nav_announcements":
                return R.id.nav_announcements;
            case "nav_disturbances":
                return R.id.nav_disturbances;
            case "nav_infrastructure":
                return R.id.nav_infrastructure;
            case "nav_notif":
                return R.id.nav_notif;
            case "nav_settings":
                return R.id.nav_settings;
            case "nav_help":
                return R.id.nav_help;
            case "nav_about":
                return R.id.nav_about;
            default:
                return 0;
        }
    }

    private Fragment getCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);
        if (currentFragment == null || !currentFragment.isAdded()) {
            currentFragment = getSupportFragmentManager().findFragmentById(R.id.alt_fragment_container);
        }
        return currentFragment;
    }

    private void replaceFragment(Fragment newFragment, boolean addToBackStack) {
        if (!(newFragment instanceof MainAddableFragment)) {
            throw new IllegalArgumentException("Fragment must implement MainAddableFragment");
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            transaction.remove(currentFragment);
        }

        if (!API.getInstance().meetsEndpointRequirements(false)) {
            newFragment = ErrorFragment.newInstance(ErrorFragment.ErrorType.VERSION_TOO_OLD,
                    ((MainAddableFragment) newFragment).getNavDrawerId(),
                    ((MainAddableFragment) newFragment).getNavDrawerIdAsString());
        } else if (((MainAddableFragment) newFragment).needsTopology() && Coordinator.get(this).getMapManager().getNetworks().isEmpty()) {
            ErrorFragment.ErrorType type = ErrorFragment.ErrorType.NO_TOPOLOGY;
            if (Coordinator.get(this).getMapManager().isTopologyUpdateInProgress()) {
                type = ErrorFragment.ErrorType.TOPOLOGY_DOWNLOADING;
            }
            newFragment = ErrorFragment.newInstance(type,
                    ((MainAddableFragment) newFragment).getNavDrawerId(),
                    ((MainAddableFragment) newFragment).getNavDrawerIdAsString());
        }
        int destContainer = R.id.main_fragment_container;
        if (!((MainAddableFragment) newFragment).isScrollable()) {
            destContainer = R.id.alt_fragment_container;
        }
        transaction.replace(destContainer, newFragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commitAllowingStateLoss();
        getSupportFragmentManager().executePendingTransactions();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Action bar menu item selected

        switch (item.getItemId()) {
            case R.id.menu_debug:
                new AsyncTask<Void, Void, String>() {

                    @Override
                    protected String doInBackground(Void... voids) {
                        if (locBound) {
                            return locService.dumpDebugInfo();
                        }
                        return "";
                    }

                    @Override
                    protected void onPostExecute(String s) {
                        if (!isFinishing() && isVisible) {
                            DialogFragment newFragment = HtmlDialogFragment.newInstance(s, false);
                            newFragment.show(getSupportFragmentManager(), "debugtext");
                        }
                    }
                }.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        Fragment newFragment = getNewFragment(item.getItemId());

        if (newFragment != null) {
            replaceFragment(newFragment, true);
        } else {
            Snackbar.make(findViewById(R.id.fab), R.string.status_not_yet_implemented, Snackbar.LENGTH_LONG).show();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onClick(String url) {
        if (url.startsWith("help:")) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_container, HelpFragment.newInstance(url.substring(5)));
            transaction.addToBackStack(null);
            transaction.commitAllowingStateLoss();
        } else if (url.startsWith("infra:")) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_container, InfraFragment.newInstance(url.substring(6)));
            transaction.addToBackStack(null);
            transaction.commitAllowingStateLoss();
        } else if (url.startsWith("page:")) {
            switchToPage(url.substring(5), true);
        } else if (url.startsWith("planroute:")) {
            try {
                Uri uri = Uri.parse(url);
                String from = uri.getQueryParameter("from"), to = uri.getQueryParameter("to");
                if (from != null && !from.isEmpty()) {
                    planRouteFrom = from;
                }
                if (to != null && !to.isEmpty()) {
                    planRouteTo = to;
                }
                switchToPage("nav_plan_route", true);
            } catch (Throwable e) {
                e.printStackTrace();
                return;
            }
        } else {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (browserIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(browserIntent);
            }
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private LocServiceConnection mConnection = new LocServiceConnection();

    @Override
    public boolean onQueryTextSubmit(String query) {
        // make submit be a no-op, our results are the "suggestions"
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onListFragmentStationSelected(Station station) {
        Intent intent = new Intent(this, StationActivity.class);
        intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
        intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
        startActivity(intent);
    }

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
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MapManager.ACTION_UPDATE_TOPOLOGY_PROGRESS:
                    final int progress = intent.getIntExtra(MapManager.EXTRA_UPDATE_TOPOLOGY_PROGRESS, 0);
                    final String msg = String.format(getString(R.string.update_topology_progress), progress);
                    if (topologyUpdateSnackbar == null) {
                        topologyUpdateSnackbar = Snackbar.make(findViewById(R.id.fab), msg, Snackbar.LENGTH_INDEFINITE)
                                .setAction(R.string.update_topology_cancel_action, view -> Coordinator.get(MainActivity.this).getMapManager().cancelTopologyUpdate());
                    } else {
                        topologyUpdateSnackbar.setText(msg);
                    }
                    if (!topologyUpdateSnackbar.isShown()) {
                        topologyUpdateSnackbar.show();
                    }
                    break;
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    final boolean success = intent.getBooleanExtra(MapManager.EXTRA_UPDATE_TOPOLOGY_FINISHED, false);
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
                case MapManager.ACTION_TOPOLOGY_UPDATE_AVAILABLE:
                    Snackbar.make(findViewById(R.id.fab), R.string.update_topology_available, 10000)
                            .setAction(R.string.update_topology_update_action, view -> Coordinator.get(MainActivity.this).getMapManager().updateTopology()).show();
                    break;
                case Coordinator.ACTION_CACHE_EXTRAS_PROGRESS:
                    final int progressCurrent = intent.getIntExtra(Coordinator.EXTRA_CACHE_EXTRAS_PROGRESS_CURRENT, 0);
                    final int progressTotal = intent.getIntExtra(Coordinator.EXTRA_CACHE_EXTRAS_PROGRESS_TOTAL, 1);
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
                case Coordinator.ACTION_CACHE_EXTRAS_FINISHED:
                    final boolean success2 = intent.getBooleanExtra(Coordinator.EXTRA_CACHE_EXTRAS_FINISHED, false);
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
                case FeedbackUtil.ACTION_FEEDBACK_PROVIDED:
                    String msg3;
                    if (intent.getBooleanExtra(FeedbackUtil.EXTRA_FEEDBACK_PROVIDED_DELAYED, false)) {
                        msg3 = getString(R.string.feedback_provided_delayed);
                    } else {
                        msg3 = getString(R.string.feedback_provided);
                    }
                    Snackbar.make(findViewById(R.id.fab), msg3, Snackbar.LENGTH_LONG).show();
                    break;
                case ReportActivity.ACTION_REPORT_PROVIDED:
                    Snackbar.make(findViewById(R.id.fab), getString(R.string.act_report_success), Snackbar.LENGTH_LONG).show();
                    break;
                case API.ACTION_ENDPOINT_META_AVAILABLE:
                case ACTION_MAIN_SERVICE_BOUND: {
                    // when the activity loads, we're not bound to the main service, and thus we cannot know if
                    // there is topology data or not, therefore we can't display the "no topology" error message
                    // now that we have bound to the main service, we can check whether the topology is present and display the error if not.
                    Fragment currentFragment = getCurrentFragment();
                    Fragment newFragment = null;
                    if (currentFragment instanceof MainAddableFragment) {
                        MapManager mapm = Coordinator.get(MainActivity.this).getMapManager();
                        if (!API.getInstance().meetsEndpointRequirements(false)) {
                            newFragment = ErrorFragment.newInstance(ErrorFragment.ErrorType.VERSION_TOO_OLD,
                                    ((MainAddableFragment) currentFragment).getNavDrawerId(),
                                    ((MainAddableFragment) currentFragment).getNavDrawerIdAsString());
                        } else if (((MainAddableFragment) currentFragment).needsTopology() &&
                                mapm.getNetworks().isEmpty()) {
                            ErrorFragment.ErrorType type = ErrorFragment.ErrorType.NO_TOPOLOGY;
                            if (mapm.isTopologyUpdateInProgress()) {
                                type = ErrorFragment.ErrorType.TOPOLOGY_DOWNLOADING;
                            }
                            newFragment = ErrorFragment.newInstance(type,
                                    ((MainAddableFragment) currentFragment).getNavDrawerId(),
                                    ((MainAddableFragment) currentFragment).getNavDrawerIdAsString());
                        }
                        if (newFragment != null) {
                            replaceFragment(newFragment, false);
                        }
                    }
                    break;
                }
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
    public void switchToPage(String pageString, boolean addToBackStack) {
        Fragment newFragment = getNewFragment(pageStringToResourceId(pageString));

        if (newFragment != null) {
            replaceFragment(newFragment, addToBackStack);
        } else {
            Snackbar.make(findViewById(R.id.fab), R.string.status_not_yet_implemented, Snackbar.LENGTH_LONG).show();
        }
    }

    private String planRouteFrom = null;
    private String planRouteTo = null;

    @Override
    public String getRouteOrigin() {
        String s = planRouteFrom;
        planRouteFrom = null;
        return s;
    }

    @Override
    public String getRouteDestination() {
        String s = planRouteTo;
        planRouteTo = null;
        return s;
    }

    @Override
    public void onListFragmentInteraction(LineRecyclerViewAdapter.LineItem item) {
        for (Network network : Coordinator.get(this).getMapManager().getNetworks()) {
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

    @Override
    public void onLinesFinishedRefreshing() {
        SwipeRefreshLayout srl = findViewById(R.id.swipe_container);
        srl.setRefreshing(false);
    }

    @Override
    public void onStatsFinishedRefreshing() {
        SwipeRefreshLayout srl = findViewById(R.id.swipe_container);
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
    public void onListFragmentConfirmButtonClick(final TripRecyclerViewAdapter.TripItem item) {
        (new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Trip.confirm(Coordinator.get(MainActivity.this).getDB(), item.id);
                return null;
            }
        }).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
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
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        Cursor cursor = (Cursor) searchView.getSuggestionsAdapter().getItem(position);
        int intentDataColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA);
        String intentData = cursor.getString(intentDataColumn);

        InternalLinkHandler.onClick(this, intentData, null);
        searchView.setIconified(true);
        searchView.clearFocus();
        // call your request, do some stuff..

        // collapse the action view
        if (searchItem != null) {
            searchItem.collapseActionView();
        }
        return true;
    }

    public static final String ACTION_MAIN_SERVICE_BOUND = "im.tny.segvault.disturbances.action.MainActivity.mainservicebound";

    public static final String EXTRA_INITIAL_FRAGMENT = "im.tny.segvault.disturbances.extra.MainActivity.initialfragment";

    public static final String EXTRA_FROM_INTRO = "im.tny.segvault.disturbances.extra.MainActivity.fromintro";

    public static final String EXTRA_PLAN_ROUTE_TO_STATION = "im.tny.segvault.disturbances.extra.MainActivity.planroute.to.station";
}
