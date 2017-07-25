package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Network;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

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
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);

        new UpdateDataTask().execute();

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

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private class UpdateDataTask extends AsyncTask<Void, Integer, Boolean> {
        private List<TripRecyclerViewAdapter.TripItem> items = new ArrayList<>();

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

            RealmResults<StationUse> uses = realm.where(StationUse.class)
                    .greaterThan("entryDate", new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(7))).findAll().where()
                    .equalTo("type", "NETWORK_ENTRY").or().equalTo("type", "VISIT").findAll();

            // now we have all station uses that **might** be part of editable trips
            // get all trips that contain these uses and which are yet to be confirmed
            RealmQuery<Trip> tripsQuery = realm.where(Trip.class);
            for(StationUse use : uses) {
                tripsQuery = tripsQuery.or().equalTo("userConfirmed", false).equalTo("path.station.id", use.getStation().getId()).equalTo("path.entryDate", use.getEntryDate());
            }
            for (Trip t : tripsQuery.findAll()) {
                TripRecyclerViewAdapter.TripItem item = new TripRecyclerViewAdapter.TripItem(t, networks);
                items.add(item);
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
            if (result && recyclerView != null && mListener != null) {
                recyclerView.setAdapter(new TripRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                // TODO empty view
                //emptyView.setVisibility(View.GONE);
            } else {
                //emptyView.setVisibility(View.VISIBLE);
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
    public interface OnListFragmentInteractionListener extends TripRecyclerViewAdapter.OnListFragmentInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    new UpdateDataTask().execute();
                    break;
            }
        }
    };
}
