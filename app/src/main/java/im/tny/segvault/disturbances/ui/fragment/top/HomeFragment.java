package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Layout;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.FeedbackUtil;
import im.tny.segvault.disturbances.InternalLinkHandler;
import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.MqttManager;
import im.tny.segvault.disturbances.OurHtmlHttpImageGetter;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.RouteUtil;
import im.tny.segvault.disturbances.S2LSChangeListener;
import im.tny.segvault.disturbances.ServiceConnectUtil;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.fragment.HomeBackersFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeFavoriteStationsFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeLinesFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeStatsFragment;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.fragment.UnconfirmedTripsFragment;
import im.tny.segvault.disturbances.ui.util.RichTextUtils;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link HomeFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;

    private TextView networkClosedView;
    private CardView networkClosedCard;
    private CardView clockUnadjustedCard;
    private CardView navigationCard;
    private CardView ongoingTripCard;
    private LinearLayout curStationLayout;
    private LinearLayout curStationIconsLayout;
    private TextView curStationNameView;
    private TextView directionView;
    private TextView nextStationView;
    private TextView nextTrainsView;
    private Button curTripIncorrectLocationButton;
    private Button curTripEndButton;
    private Button navEndButton;
    private CardView unconfirmedTripsCard;
    private CardView favoriteStationsCard;
    private Button disturbancesButton;
    private Button tripHistoryButton;

    private LinearLayout routeInstructionsLayout;
    private LinearLayout routeBodyLayout;
    private TextView routeTitleView;
    private TextView routeSummaryView;

    private HomeStatsFragment statsFragment;

    private Map<Integer, HtmlTextView> motdViews = new HashMap<>();

    private static final String ARG_NETWORK_ID = "networkId";

    private String networkId;

    private boolean refreshedOnlineSinceOpening = false;
    private boolean favoritesInMostUsedMode = false;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public boolean needsTopology() {
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_home;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_home";
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment HomeFragment.
     */
    public static HomeFragment newInstance(String networkId) {
        HomeFragment fragment = new HomeFragment();
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
        setUpActivity(getString(R.string.app_name), false, true);
        setHasOptionsMenu(true);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        motdViews.put(0, view.findViewById(R.id.motd_priority0_view));
        motdViews.put(1, view.findViewById(R.id.motd_priority1_view));
        motdViews.put(2, view.findViewById(R.id.motd_priority2_view));
        motdViews.put(3, view.findViewById(R.id.motd_priority3_view));
        motdViews.put(4, view.findViewById(R.id.motd_priority4_view));
        motdViews.put(5, view.findViewById(R.id.motd_priority5_view));
        motdViews.put(6, view.findViewById(R.id.motd_priority6_view));

        networkClosedCard = view.findViewById(R.id.network_closed_card);
        networkClosedView = view.findViewById(R.id.network_closed_view);
        clockUnadjustedCard = view.findViewById(R.id.clock_unadjusted_card);

        navigationCard = view.findViewById(R.id.navigation_card);
        ongoingTripCard = view.findViewById(R.id.ongoing_trip_card);
        curStationLayout = view.findViewById(R.id.cur_station_layout);
        curStationIconsLayout = view.findViewById(R.id.cur_station_icons_layout);
        curStationNameView = view.findViewById(R.id.cur_station_name_view);
        directionView = view.findViewById(R.id.direction_view);
        nextStationView = view.findViewById(R.id.next_station_view);
        nextTrainsView = view.findViewById(R.id.next_trains_view);
        curTripEndButton = view.findViewById(R.id.cur_trip_end);
        curTripIncorrectLocationButton = view.findViewById(R.id.cur_trip_incorrect_location);

        routeInstructionsLayout = view.findViewById(R.id.route_instructions_layout);
        routeBodyLayout = view.findViewById(R.id.route_body_layout);
        routeTitleView = view.findViewById(R.id.route_title_view);
        routeSummaryView = view.findViewById(R.id.route_summary_view);
        navEndButton = view.findViewById(R.id.end_navigation);

        unconfirmedTripsCard = view.findViewById(R.id.unconfirmed_trips_card);
        favoriteStationsCard = view.findViewById(R.id.favorite_stations_card);

        curTripEndButton.setOnClickListener(v -> {
            Intent stopIntent = new Intent(getContext(), MainService.class);
            stopIntent.setAction(MainService.ACTION_END_TRIP);
            stopIntent.putExtra(MainService.EXTRA_TRIP_NETWORK, networkId);
            getContext().startService(stopIntent);
        });

        view.findViewById(R.id.plan_route_button).setOnClickListener(view15 -> switchToPage("nav_plan_route"));
        view.findViewById(R.id.map_button).setOnClickListener(view14 -> switchToPage("nav_map"));

        disturbancesButton = view.findViewById(R.id.disturbances_button);
        disturbancesButton.setOnClickListener(view13 -> switchToPage("nav_disturbances"));
        disturbancesButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                recomputeShortcutVisibility();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    disturbancesButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    disturbancesButton.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            }
        });

        tripHistoryButton = view.findViewById(R.id.trip_history_button);
        tripHistoryButton.setOnClickListener(view12 -> switchToPage("nav_trip_history"));

        view.findViewById(R.id.clock_unadjusted_button).setOnClickListener(view1 -> startActivity(new Intent(Settings.ACTION_DATE_SETTINGS)));

        getSwipeRefreshLayout().setOnRefreshListener(() -> refresh(true, true));

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS);
        filter.addAction(S2LSChangeListener.ACTION_CURRENT_TRIP_UPDATED);
        filter.addAction(S2LSChangeListener.ACTION_CURRENT_TRIP_ENDED);
        filter.addAction(S2LSChangeListener.ACTION_S2LS_STATUS_CHANGED);
        filter.addAction(S2LSChangeListener.ACTION_NAVIGATION_ENDED);
        filter.addAction(MainService.ACTION_TRIP_TABLE_UPDATED);
        filter.addAction(MainService.ACTION_FAVORITE_STATIONS_UPDATED);
        filter.addAction(MqttManager.ACTION_VEHICLE_ETAS_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        Fragment newFragment = HomeLinesFragment.newInstance();
        transaction.replace(R.id.line_status_card, newFragment);

        statsFragment = HomeStatsFragment.newInstance(networkId);
        transaction.replace(R.id.stats_card, statsFragment);

        newFragment = HomeBackersFragment.newInstance();
        transaction.replace(R.id.footer_layout, newFragment);

        transaction.commitAllowingStateLoss();
        refresh(true, false);
        refreshCurrentTrip();
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.home, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refresh(true, true);
                return true;
            case R.id.menu_report_incorrect_location:
                new FeedbackUtil.IncorrectLocation(getContext()).showReportWizard();
                return true;
            case R.id.menu_connect_service:
                new ServiceConnectUtil.Wizard(getContext()).show();
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
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void recomputeShortcutVisibility() {
        if (disturbancesButton == null || mListener == null) {
            return;
        }
        Layout layout = disturbancesButton.getLayout();
        boolean littleSpace = false;
        if (layout == null) {
            return;
        }
        int lines = layout.getLineCount();
        if (lines > 0) {
            int ellipsisCount = layout.getEllipsisCount(lines - 1);
            if (ellipsisCount > 0) {
                littleSpace = true;
            }
        }

        if (!littleSpace) {
            disturbancesButton.setVisibility(View.VISIBLE);
            tripHistoryButton.setVisibility(View.VISIBLE);
            return;
        }

        MainService m = mListener.getMainService();
        if (m == null) {
            return;
        }
        if (Coordinator.get(getContext()).getLineStatusCache().isDisturbanceOngoing()) {
            disturbancesButton.setVisibility(View.VISIBLE);
            tripHistoryButton.setVisibility(View.GONE);
        } else {
            disturbancesButton.setVisibility(View.GONE);
            tripHistoryButton.setVisibility(View.VISIBLE);
        }
    }

    private void refresh(boolean requestOnlineUpdate, boolean manualRefresh) {
        if (mListener == null)
            return;

        new LoadUnconfirmedTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
        new LoadFavoriteTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);

        MapManager mapm = Coordinator.get(getContext()).getMapManager();
        if (requestOnlineUpdate) {
            Coordinator.get(getContext()).getLineStatusCache().updateLineStatus();
            statsFragment.onlineUpdate();
        }

        Network net = mapm.getNetwork(networkId);
        if (net == null) {
            networkClosedCard.setVisibility(View.GONE);
            unconfirmedTripsCard.setVisibility(View.GONE);
            mapm.checkForTopologyUpdates();
        } else if (net.isOpen()) {
            networkClosedCard.setVisibility(View.GONE);
        } else {
            Formatter f = new Formatter();
            DateUtils.formatDateRange(getContext(), f, net.getNextOpenTime(), net.getNextOpenTime(), DateUtils.FORMAT_SHOW_TIME, net.getTimezone().getID());
            networkClosedView.setText(String.format(getString(R.string.warning_network_closed), f.toString()));
            networkClosedCard.setVisibility(View.VISIBLE);
        }

        clockUnadjustedCard.setVisibility(API.getInstance().isClockOutOfSync() ? View.VISIBLE : View.GONE);
        recomputeShortcutVisibility();
        Date metaUpdated = API.getInstance().getMetaLastUpdated();
        boolean metaOld = metaUpdated != null && new Date().getTime() - metaUpdated.getTime() > TimeUnit.MINUTES.toMillis(2);
        new RefreshMOTDTask(HomeFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, requestOnlineUpdate && (manualRefresh || metaOld));

        if (requestOnlineUpdate) {
            refreshedOnlineSinceOpening = true;
        }
    }

    private static class LoadUnconfirmedTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<HomeFragment> parentRef;

        LoadUnconfirmedTask(HomeFragment fragment) {
            this.parentRef = new WeakReference<>(fragment);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HomeFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();

            return db.tripDao().getUnconfirmedCount(Trip.getConfirmCutoff()) > 0;
        }

        @Override
        protected void onPostExecute(Boolean hasTripsToConfirm) {
            super.onPostExecute(hasTripsToConfirm);
            HomeFragment parent = parentRef.get();
            if (parent == null || parent.getContext() == null || !parent.isAdded()) {
                return;
            }
            if (hasTripsToConfirm) {
                if (parent.unconfirmedTripsCard.getVisibility() == View.GONE) {
                    FragmentTransaction transaction = parent.getChildFragmentManager().beginTransaction();
                    Fragment newFragment = UnconfirmedTripsFragment.newInstance(1);
                    transaction.replace(R.id.unconfirmed_trips_card, newFragment);
                    transaction.commitAllowingStateLoss();
                    parent.unconfirmedTripsCard.setVisibility(View.VISIBLE);
                }
            } else {
                parent.unconfirmedTripsCard.setVisibility(View.GONE);
            }
        }
    }

    private static class LoadFavoriteTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<HomeFragment> parentRef;
        private boolean hasMostUsedStations = false;

        LoadFavoriteTask(HomeFragment fragment) {
            this.parentRef = new WeakReference<>(fragment);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HomeFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();

            hasMostUsedStations = Util.getMostUsedStations(parent.getContext(), 1).size() > 0;

            return db.stationPreferenceDao().getFavorite().size() > 0;
        }

        @Override
        protected void onPostExecute(Boolean hasFavoriteStations) {
            super.onPostExecute(hasFavoriteStations);
            HomeFragment parent = parentRef.get();
            if (parent == null || parent.getContext() == null || !parent.isAdded()) {
                return;
            }
            if (hasFavoriteStations) {
                if (parent.favoriteStationsCard.getVisibility() == View.GONE || parent.favoritesInMostUsedMode) {
                    FragmentTransaction transaction = parent.getChildFragmentManager().beginTransaction();
                    Fragment newFragment = HomeFavoriteStationsFragment.newInstance(1, false);
                    transaction.replace(R.id.favorite_stations_card, newFragment);
                    transaction.commitAllowingStateLoss();
                    parent.favoriteStationsCard.setVisibility(View.VISIBLE);
                    parent.favoritesInMostUsedMode = false;
                }
            } else if (hasMostUsedStations) {
                if (parent.favoriteStationsCard.getVisibility() == View.GONE || !parent.favoritesInMostUsedMode) {
                    FragmentTransaction transaction = parent.getChildFragmentManager().beginTransaction();
                    Fragment newFragment = HomeFavoriteStationsFragment.newInstance(1, true);
                    transaction.replace(R.id.favorite_stations_card, newFragment);
                    transaction.commitAllowingStateLoss();
                    parent.favoriteStationsCard.setVisibility(View.VISIBLE);
                    parent.favoritesInMostUsedMode = true;
                }
            } else {
                parent.favoriteStationsCard.setVisibility(View.GONE);
            }
        }
    }

    private void refreshCurrentTrip() {
        if (mListener == null)
            return;

        if (!isAdded())
            return;

        new RefreshCurrentTrip(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    private static class RefreshCurrentTrip extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<HomeFragment> parentRef;

        private S2LS loc;
        private Path currentPath;
        private Route currentRoute;
        private Stop direction;
        private Stop next;
        private Stop likelyExit = null;

        RefreshCurrentTrip(HomeFragment parent) {
            this.parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HomeFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            loc = Coordinator.get(parent.getContext()).getS2LS(parent.networkId);
            if (loc == null) {
                return false;
            }

            currentPath = loc.getCurrentTrip();
            currentRoute = loc.getCurrentTargetRoute();

            if (currentPath != null) {
                direction = currentPath.getDirection();
                next = currentPath.getNextStop();


                if (direction != null && next != null) {
                    likelyExit = RouteUtil.getLikelyNextExit(parent.getContext(), currentPath.getEdgeList(), 1);
                }
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            HomeFragment parent = parentRef.get();
            if (parent == null || !parent.isAdded()) {
                return;
            }

            if (!result || loc == null) {
                parent.ongoingTripCard.setVisibility(View.GONE);
                parent.navigationCard.setVisibility(View.GONE);
                return;
            }

            if (currentPath == null) {
                parent.ongoingTripCard.setVisibility(View.GONE);
            } else {
                final Station station = currentPath.getCurrentStop().getStation();
                parent.curStationNameView.setText(station.getName());

                Map<String, List<API.MQTTvehicleETA>> etas = Coordinator.get(parent.getContext()).getMqttManager().getVehicleETAsForStation(station, 1);

                boolean showETAs = (direction == null || station.getLines().size() > 0) && etas.size() > 0;
                if (direction != null && next != null) {
                    parent.directionView.setText(String.format(parent.getString(R.string.frag_home_trip_direction), direction.getStation().getName()));
                    parent.nextStationView.setText(String.format(parent.getString(R.string.frag_home_trip_next_station), next.getStation().getName()));


                    int resId = R.style.TextAppearance_AppCompat_Small;
                    if (next == likelyExit && currentRoute == null) {
                        resId = R.style.TextAppearance_AppCompat_Medium;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        parent.nextStationView.setTextAppearance(resId);
                    } else {
                        parent.nextStationView.setTextAppearance(parent.getContext(), resId);
                    }

                    parent.directionView.setVisibility(View.VISIBLE);
                    parent.nextStationView.setVisibility(View.VISIBLE);
                } else {
                    parent.directionView.setVisibility(View.GONE);
                    parent.nextStationView.setVisibility(View.GONE);
                    showETAs = etas.size() > 0;
                }
                parent.redrawCurrentStationLineIcons(station);
                parent.curStationLayout.setVisibility(View.VISIBLE);
                parent.curStationLayout.setOnClickListener(view -> {
                    Intent intent = new Intent(parent.getContext(), StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                    parent.startActivity(intent);
                });

                if (showETAs) {
                    List<CharSequence> etaLines = new ArrayList<>();

                    MainService.addETAlines(parent.getContext(), etas, etaLines, currentPath.getCurrentStop(), direction);
                    parent.nextTrainsView.setText("");
                    for (int i = 0; i < etaLines.size(); i++) {
                        parent.nextTrainsView.append(etaLines.get(i));
                        if (i < etaLines.size() - 1) {
                            parent.nextTrainsView.append("\n");
                        }
                    }
                    parent.nextTrainsView.setVisibility(View.VISIBLE);
                } else {
                    parent.nextTrainsView.setVisibility(View.GONE);
                }

                parent.curTripIncorrectLocationButton.setOnClickListener(view -> new FeedbackUtil.IncorrectLocation(parent.getContext(), station).showReportWizard());

                if (loc.canRequestEndOfTrip()) {
                    parent.curTripEndButton.setVisibility(View.VISIBLE);
                    parent.curTripIncorrectLocationButton.setVisibility(View.GONE);
                } else {
                    parent.curTripEndButton.setVisibility(View.GONE);
                    parent.curTripIncorrectLocationButton.setVisibility(View.VISIBLE);
                }
                parent.ongoingTripCard.setVisibility(View.VISIBLE);
            }

            if (currentRoute == null) {
                parent.navigationCard.setVisibility(View.GONE);
            } else {
                RouteUtil.RouteStepInfo info = RouteUtil.buildRouteStepInfo(parent.getContext(), currentRoute, currentPath);
                parent.routeTitleView.setText(info.title);
                parent.routeSummaryView.setText(info.summary);
                parent.routeSummaryView.setVisibility(View.VISIBLE);

                parent.routeBodyLayout.removeAllViews();
                for (CharSequence s : info.bodyLines) {
                    TextView tv = new TextView(parent.getContext());
                    if (Build.VERSION.SDK_INT < 23) {
                        tv.setTextAppearance(parent.getContext(), R.style.TextAppearance_AppCompat_Small);
                    } else {
                        tv.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
                    }
                    tv.setText(s);
                    parent.routeBodyLayout.addView(tv);
                }

                parent.navEndButton.setOnClickListener(view -> {
                    Intent stopIntent = new Intent(parent.getContext(), MainService.class);
                    stopIntent.setAction(MainService.ACTION_END_NAVIGATION);
                    stopIntent.putExtra(MainService.EXTRA_NAVIGATION_NETWORK, loc.getNetwork().getId());
                    parent.getContext().startService(stopIntent);
                });
                parent.routeInstructionsLayout.setVisibility(View.VISIBLE);
                parent.navigationCard.setVisibility(View.VISIBLE);
            }
        }
    }

    private void redrawCurrentStationLineIcons(Station station) {
        curStationIconsLayout.removeAllViews();
        List<Line> lines = new ArrayList<>(station.getLines());
        Collections.sort(lines, (l1, l2) -> Integer.valueOf(l1.getOrder()).compareTo(l2.getOrder()));

        for (Line l : lines) {
            Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(l.getId()));
            drawable.setColorFilter(l.getColor(), PorterDuff.Mode.SRC_ATOP);

            int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());
            FrameLayout iconFrame = new FrameLayout(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
            int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                params.setMarginEnd(margin);
            }
            int marginTop = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            params.setMargins(0, marginTop, margin, 0);
            iconFrame.setLayoutParams(params);
            iconFrame.setBackground(drawable);
            curStationIconsLayout.addView(iconFrame);
        }
    }

    private String getMOTDtext(API.MOTD motd) {
        final String locale = Util.getCurrentLanguage(getContext());
        if (motd.html.get(locale) != null) {
            return motd.html.get(locale);
        }
        // no translation available for this locale
        return motd.html.get(motd.mainLocale);
    }

    private void refreshMOTD(API.MOTD motd) {
        for (HtmlTextView view : motdViews.values()) {
            view.setVisibility(View.GONE);
        }
        if (motd == null || motd.html == null || motd.html.size() == 0) {
            return;
        }
        int priority = motd.priority;
        if (priority < 100 && priority > 6) {
            priority = 6;
        }
        if (priority >= 0 && priority <= 6) {
            String html = getMOTDtext(motd);
            motdViews.get(priority).setHtml(html, new OurHtmlHttpImageGetter(motdViews.get(priority), null, OurHtmlHttpImageGetter.ParentFitType.FIT_PARENT_WIDTH));
            motdViews.get(priority).setText(RichTextUtils.replaceAll(
                    (Spanned) motdViews.get(priority).getText(),
                    URLSpan.class,
                    new RichTextUtils.URLSpanConverter(),
                    new InternalLinkHandler(getContext(), mListener)));
            motdViews.get(priority).setVisibility(View.VISIBLE);
        }
    }

    private static class RefreshMOTDTask extends AsyncTask<Boolean, Void, Boolean> {
        private WeakReference<HomeFragment> parentRef;
        private API.MOTD motd;

        RefreshMOTDTask(HomeFragment parent) {
            parentRef = new WeakReference<>(parent);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            HomeFragment parent = parentRef.get();
            if (parent == null || !parent.isAdded()) {
                return;
            }
            API.Meta meta = API.getInstance().getMetaOffline();
            if (meta != null) {
                parent.refreshMOTD(meta.motd);
            }
        }

        @Override
        protected Boolean doInBackground(Boolean... forceUpdate) {
            try {
                if (forceUpdate.length > 0 && forceUpdate[0]) {
                    this.motd = API.getInstance().getMeta(true).motd;
                } else {
                    this.motd = API.getInstance().getMeta().motd;
                }
                return true;
            } catch (APIException e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            HomeFragment parent = parentRef.get();
            if (parent == null || !result || !parent.isAdded()) {
                return;
            }
            parent.refreshMOTD(motd);
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
    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener, InternalLinkHandler.FallbackLinkHandler {

    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS:
                case MainService.ACTION_TRIP_TABLE_UPDATED:
                case MainService.ACTION_FAVORITE_STATIONS_UPDATED:
                    refresh(false, false);
                    break;
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                    refresh(!refreshedOnlineSinceOpening, false);
                    refreshCurrentTrip();
                    break;
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    refresh(true, false);
                    // fallthrough
                case S2LSChangeListener.ACTION_CURRENT_TRIP_UPDATED:
                case S2LSChangeListener.ACTION_CURRENT_TRIP_ENDED:
                case S2LSChangeListener.ACTION_S2LS_STATUS_CHANGED:
                case S2LSChangeListener.ACTION_NAVIGATION_ENDED:
                case MqttManager.ACTION_VEHICLE_ETAS_UPDATED:
                    refreshCurrentTrip();
                    break;
                case API.ACTION_ENDPOINT_META_AVAILABLE:
                    new RefreshMOTDTask(HomeFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
            }
        }
    };
}
