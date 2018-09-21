package im.tny.segvault.disturbances.ui.fragment.top;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.text.Layout;
import android.text.format.DateUtils;
import android.text.format.Time;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.FeedbackUtil;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.RouteUtil;
import im.tny.segvault.disturbances.S2LSChangeListener;
import im.tny.segvault.disturbances.ServiceConnectUtil;
import im.tny.segvault.disturbances.ui.fragment.HomeBackersFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeLinesFragment;
import im.tny.segvault.disturbances.ui.fragment.HomeStatsFragment;
import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.fragment.UnconfirmedTripsFragment;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import io.realm.Realm;


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
    private Button curTripIncorrectLocationButton;
    private Button curTripEndButton;
    private Button navEndButton;
    private CardView unconfirmedTripsCard;
    private Button disturbancesButton;
    private Button tripHistoryButton;

    private LinearLayout routeInstructionsLayout;
    private LinearLayout routeBodyLayout;
    private TextView routeTitleView;
    private TextView routeSummaryView;

    private HomeStatsFragment statsFragment;

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
    public static HomeFragment newInstance() {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.app_name), false, true);
        setHasOptionsMenu(true);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

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
        curTripEndButton = view.findViewById(R.id.cur_trip_end);
        curTripIncorrectLocationButton = view.findViewById(R.id.cur_trip_incorrect_location);

        routeInstructionsLayout = view.findViewById(R.id.route_instructions_layout);
        routeBodyLayout = view.findViewById(R.id.route_body_layout);
        routeTitleView = view.findViewById(R.id.route_title_view);
        routeSummaryView = view.findViewById(R.id.route_summary_view);
        navEndButton = view.findViewById(R.id.end_navigation);

        unconfirmedTripsCard = view.findViewById(R.id.unconfirmed_trips_card);

        curTripEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(getContext(), MainService.class);
                stopIntent.setAction(MainService.ACTION_END_TRIP);
                stopIntent.putExtra(MainService.EXTRA_TRIP_NETWORK, MapManager.PRIMARY_NETWORK_ID);
                getContext().startService(stopIntent);
            }
        });

        view.findViewById(R.id.plan_route_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_plan_route");
            }
        });
        view.findViewById(R.id.map_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_map");
            }
        });

        disturbancesButton = (Button) view.findViewById(R.id.disturbances_button);
        disturbancesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_disturbances");
            }
        });
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

        tripHistoryButton = (Button) view.findViewById(R.id.trip_history_button);
        tripHistoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_trip_history");
            }
        });

        view.findViewById(R.id.clock_unadjusted_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            }
        });

        getSwipeRefreshLayout().setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh(true);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS);
        filter.addAction(S2LSChangeListener.ACTION_CURRENT_TRIP_UPDATED);
        filter.addAction(S2LSChangeListener.ACTION_CURRENT_TRIP_ENDED);
        filter.addAction(S2LSChangeListener.ACTION_S2LS_STATUS_CHANGED);
        filter.addAction(S2LSChangeListener.ACTION_NAVIGATION_ENDED);
        filter.addAction(MainService.ACTION_TRIP_REALM_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        Fragment newFragment = HomeLinesFragment.newInstance(1);
        transaction.replace(R.id.line_status_card, newFragment);

        statsFragment = HomeStatsFragment.newInstance(MapManager.PRIMARY_NETWORK_ID);
        transaction.replace(R.id.stats_card, statsFragment);

        newFragment = HomeBackersFragment.newInstance();
        transaction.replace(R.id.footer_layout, newFragment);

        transaction.commit();
        refresh(true);
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
                refresh(true);
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

    private void refresh(boolean requestOnlineUpdate) {
        Realm realm = Application.getDefaultRealmInstance(getContext());
        boolean hasTripsToConfirm = Trip.getMissingConfirmTrips(realm).size() > 0;
        realm.close();

        if (hasTripsToConfirm) {
            if (unconfirmedTripsCard.getVisibility() == View.GONE) {
                FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
                Fragment newFragment = UnconfirmedTripsFragment.newInstance(1);
                transaction.replace(R.id.unconfirmed_trips_card, newFragment);
                transaction.commitAllowingStateLoss();
                unconfirmedTripsCard.setVisibility(View.VISIBLE);
            }
        } else {
            unconfirmedTripsCard.setVisibility(View.GONE);
        }

        if (mListener == null)
            return;

        MapManager mapm = Coordinator.get(getContext()).getMapManager();
        if (requestOnlineUpdate) {
            Coordinator.get(getContext()).getLineStatusCache().updateLineStatus();
            statsFragment.onlineUpdate();
        }

        Network net = mapm.getNetwork(MapManager.PRIMARY_NETWORK_ID);
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
    }

    private void refreshCurrentTrip() {
        if (mListener == null)
            return;

        final MainService m = mListener.getMainService();
        if (m == null)
            return;

        final S2LS loc = Coordinator.get(getContext()).getS2LS(MapManager.PRIMARY_NETWORK_ID);

        if (loc == null) {
            ongoingTripCard.setVisibility(View.GONE);
            navigationCard.setVisibility(View.GONE);
            return;
        }
        final Path currentPath = loc.getCurrentTrip();
        final Route currentRoute = loc.getCurrentTargetRoute();
        if (currentPath == null) {
            ongoingTripCard.setVisibility(View.GONE);
        } else {
            final Station station = currentPath.getCurrentStop().getStation();
            curStationNameView.setText(station.getName());

            Stop direction = currentPath.getDirection();
            Stop next = currentPath.getNextStop();
            if (direction != null && next != null) {
                directionView.setText(String.format(getString(R.string.frag_home_trip_direction), direction.getStation().getName()));
                nextStationView.setText(String.format(getString(R.string.frag_home_trip_next_station), next.getStation().getName()));

                Stop likelyExit = RouteUtil.getLikelyNextExit(getContext(), currentPath.getEdgeList(), 1);
                int resId = android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Small;
                if (next == likelyExit && currentRoute == null) {
                    resId = android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Medium;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    nextStationView.setTextAppearance(resId);
                } else {
                    nextStationView.setTextAppearance(getContext(), resId);
                }

                directionView.setVisibility(View.VISIBLE);
                nextStationView.setVisibility(View.VISIBLE);
            } else {
                directionView.setVisibility(View.GONE);
                nextStationView.setVisibility(View.GONE);
            }
            redrawCurrentStationLineIcons(station);
            curStationLayout.setVisibility(View.VISIBLE);
            curStationLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getContext(), StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, MapManager.PRIMARY_NETWORK_ID);
                    startActivity(intent);
                }
            });

            curTripIncorrectLocationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new FeedbackUtil.IncorrectLocation(getContext(), station).showReportWizard();
                }
            });

            if (loc.canRequestEndOfTrip()) {
                curTripEndButton.setVisibility(View.VISIBLE);
                curTripIncorrectLocationButton.setVisibility(View.GONE);
            } else {
                curTripEndButton.setVisibility(View.GONE);
                curTripIncorrectLocationButton.setVisibility(View.VISIBLE);
            }
            ongoingTripCard.setVisibility(View.VISIBLE);
        }

        if (currentRoute == null) {
            navigationCard.setVisibility(View.GONE);
        } else {
            RouteUtil.RouteStepInfo info = RouteUtil.buildRouteStepInfo(getContext(), currentRoute, currentPath);
            routeTitleView.setText(info.title);
            routeSummaryView.setText(info.summary);
            routeSummaryView.setVisibility(View.VISIBLE);

            routeBodyLayout.removeAllViews();
            for (CharSequence s : info.bodyLines) {
                TextView tv = new TextView(getContext());
                if (Build.VERSION.SDK_INT < 23) {
                    tv.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Small);
                } else {
                    tv.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
                }
                tv.setText(s);
                routeBodyLayout.addView(tv);
            }

            navEndButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent stopIntent = new Intent(getContext(), MainService.class);
                    stopIntent.setAction(MainService.ACTION_END_NAVIGATION);
                    stopIntent.putExtra(MainService.EXTRA_NAVIGATION_NETWORK, loc.getNetwork().getId());
                    getContext().startService(stopIntent);
                }
            });
            routeInstructionsLayout.setVisibility(View.VISIBLE);
            navigationCard.setVisibility(View.VISIBLE);
        }
    }

    private void redrawCurrentStationLineIcons(Station station) {
        curStationIconsLayout.removeAllViews();
        List<Line> lines = new ArrayList<>(station.getLines());
        Collections.sort(lines, new Comparator<Line>() {
            @Override
            public int compare(Line l1, Line l2) {
                return Integer.valueOf(l1.getOrder()).compareTo(l2.getOrder());
            }
        });

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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                iconFrame.setBackgroundDrawable(drawable);
            } else {
                iconFrame.setBackground(drawable);
            }
            curStationIconsLayout.addView(iconFrame);
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
    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {

    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS:
                case MainService.ACTION_TRIP_REALM_UPDATED:
                    refresh(false);
                    break;
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    refresh(true);
                    // fallthrough
                case S2LSChangeListener.ACTION_CURRENT_TRIP_UPDATED:
                case S2LSChangeListener.ACTION_CURRENT_TRIP_ENDED:
                case S2LSChangeListener.ACTION_S2LS_STATUS_CHANGED:
                case S2LSChangeListener.ACTION_NAVIGATION_ENDED:
                    refreshCurrentTrip();
                    break;
            }
        }
    };
}
