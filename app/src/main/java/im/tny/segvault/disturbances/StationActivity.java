package im.tny.segvault.disturbances;

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
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import io.realm.Realm;

public class StationActivity extends AppCompatActivity
        implements StationGeneralFragment.OnFragmentInteractionListener,
        StationLobbyFragment.OnFragmentInteractionListener,
        StationTriviaFragment.OnFragmentInteractionListener {

    private String networkId;
    private String stationId;

    private float[] worldCoords;

    private LinearLayout lineIconsLayout;

    MainService locService;
    boolean locBound = false;

    private LocalBroadcastManager bm;

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
        setContentView(R.layout.activity_station);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.hide();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        lineIconsLayout = (LinearLayout) findViewById(R.id.line_icons_layout);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new StationPagerAdapter(getSupportFragmentManager(), this, networkId, stationId));
        tabLayout.setupWithViewPager(pager);

        bm = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.station, menu);

        Realm realm = Realm.getDefaultInstance();
        boolean isFavorite = realm.where(RStation.class).equalTo("id", stationId).findFirst().isFavorite();
        realm.close();
        MenuItem favItem = menu.findItem(R.id.menu_favorite);
        if(isFavorite) {
            favItem.setTitle(R.string.act_station_favorite);
            favItem.setIcon(R.drawable.ic_star_white_24dp);
        } else {
            favItem.setTitle(R.string.act_station_unfavorite);
            favItem.setIcon(R.drawable.ic_star_border_white_24dp);
        }
        return true;
    }

    private StationActivity.LocServiceConnection mConnection = new StationActivity.LocServiceConnection();

    @Override
    public MainService getMainService() {
        return locService;
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

            Network net = locService.getNetwork(networkId);
            if(net == null) {
                StationActivity.this.finish();
                return;
            }
            Station station = net.getStation(stationId);
            if(station == null) {
                StationActivity.this.finish();
                return;
            }
            worldCoords = station.getWorldCoordinates();

            setTitle(station.getName());
            getSupportActionBar().setTitle(station.getName());
            AppBarLayout abl = (AppBarLayout) findViewById(R.id.app_bar);
            final CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
            ctl.setTitle(station.getName());

            List<Line> lines = new ArrayList<>(station.getLines());
            Collections.sort(lines, new Comparator<Line>() {
                @Override
                public int compare(Line l1, Line l2) {
                    return l1.getName().compareTo(l2.getName());
                }
            });

            if (lines.size() > 1) {
                int colors[] = new int[lines.size() * 2];
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

                gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
                gd.setCornerRadius(0f);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    abl.setBackgroundDrawable(gd);
                } else {
                    abl.setBackground(gd);
                }
            } else {
                int color = lines.get(0).getColor();
                ctl.setContentScrimColor(color);
                ctl.setStatusBarScrimColor(color);
                abl.setBackgroundColor(color);
            }

            for (Line l : lines) {
                Drawable drawable = ContextCompat.getDrawable(StationActivity.this, Util.getDrawableResourceIdForLineId(l.getId()));
                drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

                int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics());
                FrameLayout iconFrame = new FrameLayout(StationActivity.this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
                int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    params.setMarginEnd(margin);
                }
                params.setMargins(0, 0, margin, 0);
                iconFrame.setLayoutParams(params);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    iconFrame.setBackgroundDrawable(drawable);
                } else {
                    iconFrame.setBackground(drawable);
                }
                lineIconsLayout.addView(iconFrame);
            }

            abl.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
                @Override
                public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                    if (ctl.getHeight() + verticalOffset < 2.5 * ViewCompat.getMinimumHeight(ctl)) {
                        lineIconsLayout.animate().alpha(0);
                    } else {
                        lineIconsLayout.animate().alpha(1);
                    }
                }
            });
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
            case R.id.menu_share_location:
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse(String.format(
                                Locale.ROOT, "geo:0,0?q=%f,%f(%s)",
                                worldCoords[0], worldCoords[1], getTitle())));

                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // oh well
                }
                return true;
            case R.id.menu_share_webprofile:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://perturbacoes.gbl08ma.com/s/" + stationId));
                try {
                    startActivity(browserIntent);
                } catch (ActivityNotFoundException e) {
                    // oh well
                }
                return true;
            case R.id.menu_favorite:
                Realm realm = Realm.getDefaultInstance();
                realm.beginTransaction();
                RStation rstation = realm.where(RStation.class).equalTo("id", stationId).findFirst();
                boolean isFavorite = rstation.isFavorite();
                isFavorite = !isFavorite;
                rstation.setFavorite(isFavorite);
                realm.copyToRealm(rstation);
                realm.commitTransaction();
                realm.close();
                if(isFavorite) {
                    item.setTitle(R.string.act_station_favorite);
                    item.setIcon(R.drawable.ic_star_white_24dp);
                } else {
                    item.setTitle(R.string.act_station_unfavorite);
                    item.setIcon(R.drawable.ic_star_border_white_24dp);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class StationPagerAdapter extends FragmentPagerAdapter {
        private static int NUM_ITEMS = 3;

        private String networkId;
        private String stationId;
        private Context context;

        public StationPagerAdapter(FragmentManager fragmentManager, Context context, String networkId, String stationId) {
            super(fragmentManager);
            this.context = context;
            this.networkId = networkId;
            this.stationId = stationId;
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return StationGeneralFragment.newInstance(networkId, stationId);
                case 1:
                    return StationLobbyFragment.newInstance(networkId, stationId);
                case 2:
                    return StationTriviaFragment.newInstance(networkId, stationId);
                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return context.getString(R.string.act_station_tab_general);
                case 1:
                    return context.getString(R.string.act_station_tab_lobbies);
                case 2:
                    return context.getString(R.string.act_station_tab_trivia);
                default:
                    return null;
            }
        }

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

    public static final String ACTION_MAIN_SERVICE_BOUND = "im.tny.segvault.disturbances.action.StationActivity.mainservicebound";

}
