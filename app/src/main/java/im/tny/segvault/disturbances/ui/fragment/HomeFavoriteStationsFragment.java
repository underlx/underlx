package im.tny.segvault.disturbances.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.MqttManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.StationPreference;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.adapter.StationRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.adapter.TripRecyclerViewAdapter;
import im.tny.segvault.subway.Station;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class HomeFavoriteStationsFragment extends Fragment {

    private static final String ARG_COLUMN_COUNT = "column-count";
    private static final String ARG_IS_MOST_USED = "is-most-used";
    private int mColumnCount = 1;
    private boolean isMostUsed = false;
    private OnListFragmentInteractionListener mListener;
    private TextView titleView;
    private RecyclerView recyclerView = null;
    private int mqttPartyID = -1;
    private String[] prevTopics = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public HomeFavoriteStationsFragment() {
    }

    public static HomeFavoriteStationsFragment newInstance(int columnCount, boolean isMostUsed) {
        HomeFavoriteStationsFragment fragment = new HomeFavoriteStationsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        args.putBoolean(ARG_IS_MOST_USED, isMostUsed);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            isMostUsed = getArguments().getBoolean(ARG_IS_MOST_USED);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_favorite_stations, container, false);

        // Set the adapter
        Context context = view.getContext();
        titleView = view.findViewById(R.id.favorite_stations_title_view);
        recyclerView = view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_FAVORITE_STATIONS_UPDATED);
        filter.addAction(MqttManager.ACTION_VEHICLE_ETAS_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);

        return view;
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

    private boolean fragmentPaused;

    @Override
    public void onResume() {
        super.onResume();
        fragmentPaused = false;
        new UpdateDataTask(false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onPause() {
        super.onPause();
        fragmentPaused = true;
        if (mqttPartyID >= 0) {
            final MqttManager mqttManager = Coordinator.get(getContext()).getMqttManager();
            mqttManager.disconnect(mqttPartyID);
            mqttPartyID = -1;
            prevTopics = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private List<StationRecyclerViewAdapter.StationItem> items = new ArrayList<>();

    private class UpdateDataTask extends AsyncTask<Void, Integer, Boolean> {
        private boolean etasOnly;

        public UpdateDataTask(boolean etasOnly) {
            this.etasOnly = etasOnly;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected synchronized Boolean doInBackground(Void... v) {
            if (mListener == null) {
                return false;
            }

            if (etasOnly) {
                return true;
            }
            items = new ArrayList<>();


            if (isMostUsed) {
                for (Station s : Util.getMostUsedStations(getContext(), 4)) {
                    items.add(new StationRecyclerViewAdapter.StationItem(s.getId(), s.getNetwork().getId(), getContext()));
                }
            } else {
                // show true favorites
                AppDatabase db = Coordinator.get(getContext()).getDB();
                List<StationPreference> favorite = db.stationPreferenceDao().getFavorite();
                for(StationPreference f : favorite) {
                    items.add(new StationRecyclerViewAdapter.StationItem(f.stationID, f.networkID, getContext()));
                }
            }


            if (items.size() == 0) {
                return true;
            }

            Collections.sort(items, (stationItem, t1) -> {
                // sorting by ID is not ideal but at least it's stable
                return stationItem.id.compareTo(t1.id);
            });
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected synchronized void onPostExecute(Boolean result) {
            if (!isAdded()) {
                // prevent onPostExecute from doing anything if no longer attached to an activity
                return;
            }
            if (result && recyclerView != null && mListener != null) {
                recyclerView.setAdapter(new StationRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
            }
            if (isMostUsed) {
                titleView.setText(R.string.frag_favorite_stations_most_used_title);
            } else {
                titleView.setText(R.string.frag_favorite_stations_title);
            }

            final MqttManager mqttManager = Coordinator.get(getContext()).getMqttManager();
            String[] topics = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                topics[i] = mqttManager.getVehicleETAsTopicForStation(items.get(i).networkId, items.get(i).id);
            }
            if (mqttPartyID >= 0) {
                if (prevTopics != null) {
                    if (prevTopics.length == topics.length) {
                        boolean containsAll = true;
                        List<String> pt = Arrays.asList(prevTopics);
                        for (String topic : topics) {
                            if (!pt.contains(topic)) {
                                containsAll = false;
                                break;
                            }
                        }
                        if (containsAll) {
                            // no changes since last subscription
                            return;
                        }
                    }
                }
                mqttManager.subscribe(mqttPartyID, topics);
            } else if (!fragmentPaused) { // since this AsyncTask started, the fragment may have paused
                mqttPartyID = mqttManager.connect(topics);
            }
            prevTopics = topics;
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
    public interface OnListFragmentInteractionListener extends TripRecyclerViewAdapter.OnListFragmentInteractionListener {
        void onListFragmentStationSelected(Station station);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                case MainService.ACTION_FAVORITE_STATIONS_UPDATED:
                    new UpdateDataTask(false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
                case MqttManager.ACTION_VEHICLE_ETAS_UPDATED:
                    new UpdateDataTask(true).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
            }
        }
    };
}
