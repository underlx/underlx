package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.FeedbackUtil;
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
import im.tny.segvault.s2ls.S2LS;
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
    private CardView ongoingTripCard;
    private LinearLayout curStationLayout;
    private LinearLayout curStationIconsLayout;
    private TextView curStationNameView;
    private TextView directionView;
    private TextView nextStationView;
    private Button curTripIncorrectLocationButton;
    private Button curTripEndButton;
    private CardView unconfirmedTripsCard;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HomeFragment newInstance(/*String param1, String param2*/) {
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
        setUpActivity(getString(R.string.app_name), R.id.nav_home, false, true);
        setHasOptionsMenu(true);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        networkClosedCard = (CardView) view.findViewById(R.id.network_closed_card);
        networkClosedView = (TextView) view.findViewById(R.id.network_closed_view);

        ongoingTripCard = (CardView) view.findViewById(R.id.ongoing_trip_card);
        curStationLayout = (LinearLayout) view.findViewById(R.id.cur_station_layout);
        curStationIconsLayout = (LinearLayout) view.findViewById(R.id.cur_station_icons_layout);
        curStationNameView = (TextView) view.findViewById(R.id.cur_station_name_view);
        directionView = (TextView) view.findViewById(R.id.direction_view);
        nextStationView = (TextView) view.findViewById(R.id.next_station_view);
        curTripEndButton = (Button) view.findViewById(R.id.cur_trip_end);
        curTripIncorrectLocationButton = (Button) view.findViewById(R.id.cur_trip_incorrect_location);

        unconfirmedTripsCard = (CardView) view.findViewById(R.id.unconfirmed_trips_card);

        curTripEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(getContext(), MainService.class);
                stopIntent.setAction(MainService.ACTION_END_TRIP);
                stopIntent.putExtra(MainService.EXTRA_TRIP_NETWORK, MainService.PRIMARY_NETWORK_ID);
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
        view.findViewById(R.id.disturbances_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_disturbances");
            }
        });
        view.findViewById(R.id.disturbances_button).setSelected(true);
        view.findViewById(R.id.trip_history_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToPage("nav_trip_history");
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
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS);
        filter.addAction(MainService.ACTION_CURRENT_TRIP_UPDATED);
        filter.addAction(MainService.ACTION_CURRENT_TRIP_ENDED);
        filter.addAction(MainService.ACTION_S2LS_STATUS_CHANGED);
        filter.addAction(MainService.ACTION_TRIP_REALM_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        Fragment newFragment = HomeLinesFragment.newInstance(1);
        transaction.replace(R.id.line_status_card, newFragment);
        newFragment = HomeStatsFragment.newInstance(MainService.PRIMARY_NETWORK_ID);
        transaction.replace(R.id.stats_card, newFragment);
        transaction.commit();
        refresh(true);
        refreshCurrentTrip();
        return view;
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
                if (mListener == null)
                    return true;

                MainService m = mListener.getMainService();
                if (m == null)
                    return true;
                new FeedbackUtil.IncorrectLocation(getContext(), m).showReportWizard();
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

        MainService m = mListener.getMainService();
        if (m == null)
            return;

        if (requestOnlineUpdate) {
            m.getLineStatusCache().updateLineStatus();
            m.getStatsCache().updateStats();
        }

        Network net = m.getNetwork(MainService.PRIMARY_NETWORK_ID);
        if (net == null) {
            networkClosedCard.setVisibility(View.GONE);
            unconfirmedTripsCard.setVisibility(View.GONE);
            m.checkForTopologyUpdates();
        } else if (net.isOpen()) {
            networkClosedCard.setVisibility(View.GONE);
        } else {
            Formatter f = new Formatter();
            DateUtils.formatDateRange(getContext(), f, net.getNextOpenTime(), net.getNextOpenTime(), DateUtils.FORMAT_SHOW_TIME, Time.TIMEZONE_UTC);
            networkClosedView.setText(String.format(getString(R.string.warning_network_closed), f.toString()));
            networkClosedCard.setVisibility(View.VISIBLE);
        }
    }

    private void refreshCurrentTrip() {
        if (mListener == null)
            return;

        final MainService m = mListener.getMainService();
        if (m == null)
            return;

        S2LS loc = m.getS2LS(MainService.PRIMARY_NETWORK_ID);

        if (loc == null || loc.getCurrentTrip() == null) {
            ongoingTripCard.setVisibility(View.GONE);
        } else {
            final Station station = loc.getCurrentTrip().getCurrentStop().getStation();
            curStationNameView.setText(station.getName());

            Stop direction = loc.getCurrentTrip().getDirection();
            Stop next = loc.getCurrentTrip().getNextStop();
            if (direction != null && next != null) {
                directionView.setText(String.format(getString(R.string.frag_home_trip_direction), direction.getStation().getName()));
                nextStationView.setText(String.format(getString(R.string.frag_home_trip_next_station), next.getStation().getName()));

                Stop likelyExit = m.getLikelyNextExit(loc.getCurrentTrip().getEdgeList(), 1);
                int resId = android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Small;
                if (next == likelyExit) {
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
            curStationLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getContext(), StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, MainService.PRIMARY_NETWORK_ID);
                    startActivity(intent);
                }
            });

            curTripIncorrectLocationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new FeedbackUtil.IncorrectLocation(getContext(), m, station).showReportWizard();
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
    }

    private void redrawCurrentStationLineIcons(Station station) {
        curStationIconsLayout.removeAllViews();
        List<Line> lines = new ArrayList<>(station.getLines());
        Collections.sort(lines, new Comparator<Line>() {
            @Override
            public int compare(Line l1, Line l2) {
                return l1.getName().compareTo(l2.getName());
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
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS:
                case MainService.ACTION_TRIP_REALM_UPDATED:
                    refresh(false);
                    break;
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    refresh(true);
                    // fallthrough
                case MainService.ACTION_CURRENT_TRIP_UPDATED:
                case MainService.ACTION_CURRENT_TRIP_ENDED:
                case MainService.ACTION_S2LS_STATUS_CHANGED:
                    refreshCurrentTrip();
                    break;
            }
        }
    };
}
