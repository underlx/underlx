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

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InvalidObjectException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.adapter.TripRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.util.SimpleDividerItemDecoration;
import im.tny.segvault.subway.Network;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class UnconfirmedTripsFragment extends Fragment {

    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;
    private RecyclerView recyclerView = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public UnconfirmedTripsFragment() {
    }

    public static UnconfirmedTripsFragment newInstance(int columnCount) {
        UnconfirmedTripsFragment fragment = new UnconfirmedTripsFragment();
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
        View view = inflater.inflate(R.layout.fragment_unconfirmed_trips, container, false);

        // Set the adapter
        Context context = view.getContext();
        recyclerView = view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(context));

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_TRIP_TABLE_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);

        AppDatabase db = Coordinator.get(getContext()).getDB();
        db.tripDao().getUnconfirmedLive(Trip.getConfirmCutoff()).observe(this,
                trips -> new UpdateTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR));

        return view;
    }

    private static class UpdateTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<UnconfirmedTripsFragment> parentRef;
        private List<TripRecyclerViewAdapter.TripItem> items = new ArrayList<>();

        UpdateTask(UnconfirmedTripsFragment fragment) {
            this.parentRef = new WeakReference<>(fragment);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            UnconfirmedTripsFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();

            Collection<Network> networks = Coordinator.get(parent.getContext()).getMapManager().getNetworks();
            for (Trip t : db.tripDao().getUnconfirmed(Trip.getConfirmCutoff())) {
                try {
                    TripRecyclerViewAdapter.TripItem item = new TripRecyclerViewAdapter.TripItem(db, t, networks);
                    items.add(item);
                } catch (InvalidObjectException e) {
                    e.printStackTrace();
                }
            }
            if (items.size() == 0) {
                return parent.isAdded();
            }
            Collections.sort(items, Collections.<TripRecyclerViewAdapter.TripItem>reverseOrder((tripItem, t1) -> Long.compare(tripItem.originTime.getTime(), t1.originTime.getTime())));

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            UnconfirmedTripsFragment parent = parentRef.get();
            if (aBoolean && parent != null && parent.isAdded()) {
                if (parent.recyclerView != null && parent.mListener != null) {
                    parent.recyclerView.setAdapter(new TripRecyclerViewAdapter(items, parent.mListener, true));
                    parent.recyclerView.invalidate();
                }
            }
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
        mListener = null;
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
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    new UpdateTask(UnconfirmedTripsFragment.this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
                    break;
            }
        }
    };
}
