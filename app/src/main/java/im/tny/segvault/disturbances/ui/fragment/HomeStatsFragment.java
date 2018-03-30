package im.tny.segvault.disturbances.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.StatsCache;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

import static android.content.Context.MODE_PRIVATE;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class HomeStatsFragment extends Fragment {
    private OnFragmentInteractionListener mListener;
    private ProgressBar progressBar = null;
    private HtmlTextView lastDisturbanceView = null;
    private TextView updateInformationView = null;
    private TextView usersOnlineView = null;
    private TableLayout lineStatsLayout = null;

    private static final String ARG_NETWORK_ID = "networkId";

    private String networkId;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public HomeStatsFragment() {
    }

    public static HomeStatsFragment newInstance(String networkId) {
        HomeStatsFragment fragment = new HomeStatsFragment();
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
        View view = inflater.inflate(R.layout.fragment_home_stats, container, false);

        Context context = view.getContext();

        lineStatsLayout = (TableLayout) view.findViewById(R.id.line_stats_layout);
        progressBar = (ProgressBar) view.findViewById(R.id.loading_indicator);
        lastDisturbanceView = (HtmlTextView) view.findViewById(R.id.last_disturbance_view);
        usersOnlineView = (TextView) view.findViewById(R.id.users_online_view);
        progressBar.setVisibility(View.VISIBLE);
        updateInformationView = (TextView) view.findViewById(R.id.update_information);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(StatsCache.ACTION_STATS_UPDATE_STARTED);
        filter.addAction(StatsCache.ACTION_STATS_UPDATE_SUCCESS);
        filter.addAction(StatsCache.ACTION_STATS_UPDATE_FAILED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);
        redraw(context);
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void redraw(Context context) {
        if (mListener == null || mListener.getMainService() == null) {
            return;
        }

        StatsCache cache = mListener.getMainService().getStatsCache();
        if (cache == null) {
            return;
        }

        StatsCache.Stats stats = cache.getNetworkStats(networkId);
        if (stats == null) {
            return;
        }

        // Calculate days difference like the website

        Calendar today = Calendar.getInstance();
        today.setTime(new Date());
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar lastDisturbance = Calendar.getInstance();
        lastDisturbance.setTime(stats.lastDisturbance);
        lastDisturbance.set(Calendar.HOUR_OF_DAY, 0);
        lastDisturbance.set(Calendar.MINUTE, 0);
        lastDisturbance.set(Calendar.SECOND, 0);
        lastDisturbance.set(Calendar.MILLISECOND, 0);

        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean locationEnabled = sharedPref.getBoolean(PreferenceNames.LocationEnable, true);
        if(locationEnabled) {
            if (stats.curOnInTransit == 0) {
                usersOnlineView.setText(R.string.frag_stats_few_users_in_network);
            } else {
                usersOnlineView.setText(String.format(getString(R.string.frag_stats_users_in_network), stats.curOnInTransit));
            }
            usersOnlineView.setVisibility(View.VISIBLE);
        } else {
            usersOnlineView.setVisibility(View.GONE);
        }

        long days = (today.getTime().getTime() - lastDisturbance.getTime().getTime()) / (24 * 60 * 60 * 1000);

        if (days < 2) {
            long hours = (new Date().getTime() - stats.lastDisturbance.getTime()) / (60 * 60 * 1000);
            lastDisturbanceView.setHtml(String.format(getString(R.string.frag_stats_last_disturbance_hours), hours));
        } else {
            lastDisturbanceView.setHtml(String.format(getString(R.string.frag_stats_last_disturbance_days), days));
        }


        Network net = mListener.getMainService().getNetwork(networkId);
        if (net == null) {
            return;
        }

        List<Line> lines = new ArrayList<>(net.getLines());

        Collections.sort(lines, new Comparator<Line>() {
            @Override
            public int compare(Line t0, Line t1) {
                return Integer.valueOf(t0.getOrder()).compareTo(t1.getOrder());
            }
        });

        lineStatsLayout.removeAllViews();
        TableRow header = (TableRow)getActivity().getLayoutInflater().inflate(R.layout.line_stats_header, lineStatsLayout, false);
        lineStatsLayout.addView(header);
        for(Line line : lines) {
            double weekAvail;
            if(stats.weekLineStats.get(line.getId()) == null) {
                continue;
            } else {
                weekAvail = stats.weekLineStats.get(line.getId()).availability;
            }

            double monthAvail;
            if(stats.monthLineStats.get(line.getId()) == null) {
                continue;
            } else {
                monthAvail = stats.monthLineStats.get(line.getId()).availability;
            }

            TableRow row = (TableRow)getActivity().getLayoutInflater().inflate(R.layout.line_stats_row, lineStatsLayout, false);
            TextView lineNameView = (TextView)row.findViewById(R.id.line_name_view);
            lineNameView.setText(Util.getLineNames(getContext(), line)[0]);
            lineNameView.setTextColor(line.getColor());

            ((TextView)row.findViewById(R.id.week_availability_view)).setText(String.format("%.3f%%", weekAvail * 100));
            ((TextView)row.findViewById(R.id.month_availability_view)).setText(String.format("%.3f%%", monthAvail * 100));

            lineStatsLayout.addView(row);
        }

        if(new Date().getTime() - stats.updated.getTime() > java.util.concurrent.TimeUnit.MINUTES.toMillis(5)) {
            lineStatsLayout.setAlpha(0.6f);
            lastDisturbanceView.setAlpha(0.6f);
            usersOnlineView.setAlpha(0.6f);
            updateInformationView.setTypeface(null, Typeface.BOLD);
        } else {
            lineStatsLayout.setAlpha(1f);
            lastDisturbanceView.setAlpha(1f);
            usersOnlineView.setAlpha(1f);
            updateInformationView.setTypeface(null, Typeface.NORMAL);
        }
        updateInformationView.setText(String.format(getString(R.string.frag_stats_updated),
                DateUtils.getRelativeTimeSpanString(context, stats.updated.getTime(), true)));
    }

    public interface OnFragmentInteractionListener {
        void onStatsFinishedRefreshing();

        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case StatsCache.ACTION_STATS_UPDATE_STARTED:
                    progressBar.setVisibility(View.VISIBLE);
                    break;
                case StatsCache.ACTION_STATS_UPDATE_SUCCESS:
                case StatsCache.ACTION_STATS_UPDATE_FAILED:
                    if (mListener != null) {
                        mListener.onStatsFinishedRefreshing();
                    }
                    progressBar.setVisibility(View.GONE);
                    // fallthrough
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                    redraw(context);
                    break;
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    break;
            }
        }
    };
}
