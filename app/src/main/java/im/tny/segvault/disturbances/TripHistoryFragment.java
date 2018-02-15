package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Network;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class TripHistoryFragment extends TopFragment {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;

    private RecyclerView recyclerView = null;
    private TextView emptyView;

    private TextView tripCountView;
    private TextView tripTotalLengthView;
    private TextView tripTotalTimeView;
    private TextView tripAverageSpeedView;

    private boolean showVisits = false;
    private Menu menu;

    private Realm realm = Realm.getDefaultInstance();

    /**
     * Mandatory constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TripHistoryFragment() {

    }

    @SuppressWarnings("unused")
    public static TripHistoryFragment newInstance(int columnCount) {
        TripHistoryFragment fragment = new TripHistoryFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_trip_history_title), R.id.nav_trip_history, false, false);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_trip_history_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        emptyView = (TextView) view.findViewById(R.id.no_trips_view);
        tripCountView = (TextView) view.findViewById(R.id.trip_count_view);
        tripTotalLengthView = (TextView) view.findViewById(R.id.trip_total_length_view);
        tripTotalTimeView = (TextView) view.findViewById(R.id.trip_total_time_view);
        tripAverageSpeedView = (TextView) view.findViewById(R.id.trip_average_speed_view);
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(context));

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        view.findViewById(R.id.stats_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), StatsActivity.class);
                startActivity(intent);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_TRIP_REALM_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);

        new TripHistoryFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.trip_history, menu);
        if(showVisits) {
            menu.findItem(R.id.menu_show_visits).setVisible(false);
        } else {
            menu.findItem(R.id.menu_hide_visits).setVisible(false);
        }
        this.menu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_show_visits:
                showVisits = true;
                item.setVisible(false);
                menu.findItem(R.id.menu_hide_visits).setVisible(true);
                new TripHistoryFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                return true;
            case R.id.menu_hide_visits:
                showVisits = false;
                item.setVisible(false);
                menu.findItem(R.id.menu_show_visits).setVisible(true);
                new TripHistoryFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        realm.close();
        mListener = null;
    }

    private class UpdateDataTask extends AsyncTask<Void, Integer, Boolean> {
        private List<TripRecyclerViewAdapter.TripItem> items = new ArrayList<>();
        private int tripCount = 0;
        private int tripTotalLength = 0;
        private int tripTotalTimeableLength = 0;
        private long tripTotalTime = 0;
        private long tripTotalMovementTime = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected Boolean doInBackground(Void... v) {
            while (mListener == null || mListener.getMainService() == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            Collection<Network> networks = mListener.getMainService().getNetworks();
            Realm realm = Realm.getDefaultInstance();
            for (Trip t : realm.where(Trip.class).findAll()) {
                TripRecyclerViewAdapter.TripItem item = new TripRecyclerViewAdapter.TripItem(t, networks);
                if(!item.isVisit) {
                    tripCount++;
                    tripTotalLength += item.length;
                    tripTotalTimeableLength += item.timeableLength;
                    tripTotalTime += item.destTime.getTime() - item.originTime.getTime();
                    tripTotalMovementTime += item.movementMilliseconds;
                }
                if (showVisits || !item.isVisit) {
                    items.add(item);
                }
            }
            realm.close();
            if (items.size() == 0) {
                return false;
            }
            Collections.sort(items, Collections.<TripRecyclerViewAdapter.TripItem>reverseOrder(new Comparator<TripRecyclerViewAdapter.TripItem>() {
                @Override
                public int compare(TripRecyclerViewAdapter.TripItem tripItem, TripRecyclerViewAdapter.TripItem t1) {
                    return Long.valueOf(tripItem.originTime.getTime()).compareTo(Long.valueOf(t1.originTime.getTime()));
                }
            }));
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            if (!isAdded()) {
                // prevent onPostExecute from doing anything if no longer attached to an activity
                return;
            }
            tripCountView.setText(Integer.toString(tripCount));
            tripTotalLengthView.setText(String.format(getString(R.string.frag_trip_history_length_value), (double)tripTotalLength / 1000f));

            long days = tripTotalTime / TimeUnit.DAYS.toMillis(1);
            long hours = (tripTotalTime % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1);
            long minutes = (tripTotalTime % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
            if (days == 0) {
                tripTotalTimeView.setText(String.format(getString(R.string.frag_trip_history_duration_no_days), hours, minutes));
            } else {
                tripTotalTimeView.setText(String.format(getString(R.string.frag_trip_history_duration_with_days), days, hours, minutes));
            }
            tripAverageSpeedView.setText("--");
            if (result && recyclerView != null && mListener != null) {
                recyclerView.setAdapter(new TripRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                emptyView.setVisibility(View.GONE);

                if (tripTotalMovementTime > 0) {
                    tripAverageSpeedView.setText(String.format(getString(R.string.frag_trip_history_speed_value),
                            ((double) tripTotalTimeableLength / (double) (tripTotalMovementTime / 1000)) * 3.6));
                }
            } else {
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener extends OnInteractionListener, TripRecyclerViewAdapter.OnListFragmentInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                case MainService.ACTION_TRIP_REALM_UPDATED:
                    if (getActivity() != null) {
                        new UpdateDataTask().execute();
                    }
                    break;
            }
        }
    };
}
