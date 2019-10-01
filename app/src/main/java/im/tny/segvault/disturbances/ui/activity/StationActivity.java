package im.tny.segvault.disturbances.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.ui.util.AppBarStateChangeListener;
import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.fragment.station.StationGeneralFragment;
import im.tny.segvault.disturbances.ui.fragment.station.StationLobbyFragment;
import im.tny.segvault.disturbances.ui.fragment.station.StationPOIFragment;
import im.tny.segvault.disturbances.ui.fragment.station.StationTriviaFragment;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.disturbances.ui.util.CustomFAB;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import io.realm.Realm;

public class StationActivity extends TopActivity
        implements StationGeneralFragment.OnFragmentInteractionListener,
        StationLobbyFragment.OnFragmentInteractionListener,
        StationPOIFragment.OnFragmentInteractionListener,
        StationTriviaFragment.OnFragmentInteractionListener {

    private String networkId;
    private String stationId;
    private String lobbyId;
    private int exitId = -1;

    private float[] worldCoords;

    private LinearLayout lineIconsLayout;

    private LocalBroadcastManager bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            networkId = getIntent().getStringExtra(EXTRA_NETWORK_ID);
            stationId = getIntent().getStringExtra(EXTRA_STATION_ID);
            lobbyId = getIntent().getStringExtra(EXTRA_LOBBY_ID);
            exitId = getIntent().getIntExtra(EXTRA_EXIT_ID, -1);
        } else {
            networkId = savedInstanceState.getString(STATE_NETWORK_ID);
            stationId = savedInstanceState.getString(STATE_STATION_ID);
        }

        setContentView(R.layout.activity_station);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final CustomFAB fab = findViewById(R.id.fab);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        lineIconsLayout = findViewById(R.id.line_icons_layout);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager pager = findViewById(R.id.pager);
        pager.setAdapter(new StationPagerAdapter(getSupportFragmentManager(), this, networkId, stationId, lobbyId));
        tabLayout.setupWithViewPager(pager);
        if(lobbyId != null && !lobbyId.isEmpty()) {
            pager.setCurrentItem(1);
        }

        bm = LocalBroadcastManager.getInstance(this);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(StationActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, "nav_plan_route");
            intent.putExtra(MainActivity.EXTRA_PLAN_ROUTE_TO_STATION, stationId);
            startActivity(intent);
        });

        AppBarLayout appBarLayout = findViewById(R.id.app_bar);
        appBarLayout.addOnOffsetChangedListener(new AppBarStateChangeListener() {
            @Override
            public void onStateChanged(AppBarLayout appBarLayout, State state) {
                if (state == State.COLLAPSED) {
                    fab.hide();
                } else {
                    fab.show();
                }
            }
        });

        Network net = Coordinator.get(this).getMapManager().getNetwork(networkId);
        if (net == null) {
            StationActivity.this.finish();
            return;
        }
        Station station = net.getStation(stationId);
        if (station == null) {
            StationActivity.this.finish();
            return;
        }
        worldCoords = station.getWorldCoordinates();

        setTitle(station.getName());
        getSupportActionBar().setTitle(station.getName());
        AppBarLayout abl = findViewById(R.id.app_bar);
        final CollapsingToolbarLayout ctl = findViewById(R.id.toolbar_layout);
        ctl.setTitle(station.getName());

        List<Line> lines = new ArrayList<>(station.getLines());
        Collections.sort(lines, (l1, l2) -> Integer.valueOf(l1.getOrder()).compareTo(l2.getOrder()));

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

        abl.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            if (ctl.getHeight() + verticalOffset < 2.5 * ViewCompat.getMinimumHeight(ctl)) {
                lineIconsLayout.animate().alpha(0);
            } else {
                lineIconsLayout.animate().alpha(1);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.station, menu);

        Realm realm = Application.getDefaultRealmInstance(this);
        RStation rstation = realm.where(RStation.class).equalTo("id", stationId).findFirst();
        boolean isFavorite = false;
        if (rstation != null) {
            isFavorite = rstation.isFavorite();
        }
        realm.close();
        MenuItem favItem = menu.findItem(R.id.menu_favorite);
        if (isFavorite) {
            favItem.setTitle(R.string.act_station_favorite);
            favItem.setIcon(R.drawable.ic_star_white_24dp);
        } else {
            favItem.setTitle(R.string.act_station_unfavorite);
            favItem.setIcon(R.drawable.ic_star_border_white_24dp);
        }
        return true;
    }

    public int getPreselectedExitId() {
        return exitId;
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
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, getString(R.string.link_format_station), stationId));
                try {
                    startActivity(shareIntent);
                } catch (ActivityNotFoundException e) {
                    // oh well
                }
                return true;
            case R.id.menu_favorite:
                Realm realm = Application.getDefaultRealmInstance(this);
                realm.beginTransaction();
                RStation rstation = realm.where(RStation.class).equalTo("id", stationId).findFirst();
                boolean isFavorite = rstation.isFavorite();
                isFavorite = !isFavorite;
                rstation.setFavorite(isFavorite);
                realm.copyToRealm(rstation);
                realm.commitTransaction();
                realm.close();
                if (isFavorite) {
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
        private static int NUM_ITEMS = 4;

        private String networkId;
        private String stationId;
        private String lobbyId;
        private Context context;

        public StationPagerAdapter(FragmentManager fragmentManager, Context context, String networkId, String stationId, String lobbyId) {
            super(fragmentManager);
            this.context = context;
            this.networkId = networkId;
            this.stationId = stationId;
            this.lobbyId = lobbyId;
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
                    return StationPOIFragment.newInstance(networkId, stationId);
                case 3:
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
                    return context.getString(R.string.act_station_tab_pois);
                case 3:
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

    public static final String EXTRA_EXIT_ID = "im.tny.segvault.disturbances.extra.StationActivity.exitid";
    public static final String EXTRA_LOBBY_ID = "im.tny.segvault.disturbances.extra.StationActivity.lobbyid";
    public static final String EXTRA_STATION_ID = "im.tny.segvault.disturbances.extra.StationActivity.stationid";
    public static final String EXTRA_NETWORK_ID = "im.tny.segvault.disturbances.extra.StationActivity.networkid";

    public static final String ACTION_MAIN_SERVICE_BOUND = "im.tny.segvault.disturbances.action.StationActivity.mainservicebound";

}
