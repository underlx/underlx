package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class DisturbanceFragment extends Fragment {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;

    private RecyclerView recyclerView = null;
    private ProgressBar progressBar = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DisturbanceFragment() {
    }

    @SuppressWarnings("unused")
    public static DisturbanceFragment newInstance(int columnCount) {
        DisturbanceFragment fragment = new DisturbanceFragment();
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
        if (mListener != null) {
            mListener.setActionBarTitle(getString(R.string.frag_disturbances_title));
            mListener.checkNavigationDrawerItem(R.id.nav_disturbances);
        }
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();

        View view = inflater.inflate(R.layout.fragment_disturbance_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        recyclerView.setVisibility(View.GONE);
        progressBar = (ProgressBar) view.findViewById(R.id.loading_indicator);
        progressBar.setVisibility(View.VISIBLE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_LOCATION_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);
        if (mListener != null && mListener.getLocationService() != null) {
            new DisturbanceFragment.UpdateDataTask().execute(context);
        }
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

    private class UpdateDataTask extends AsyncTask<Context, Integer, Boolean> {
        private Context context;
        private List<DisturbanceRecyclerViewAdapter.DisturbanceItem> items = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            recyclerView.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        }

        protected Boolean doInBackground(Context... context) {
            this.context = context[0];
            if (!Connectivity.isConnected(this.context)) {
                return false;
            }
            if (mListener == null || mListener.getLocationService() == null) {
                return false;
            }
            List<Line> lines = new LinkedList<>();
            for (Network n : mListener.getLocationService().getNetworks()) {
                lines.addAll(n.getLines());
            }
            Collection<Network> networks = mListener.getLocationService().getNetworks();
            try {
                List<API.Disturbance> disturbances = API.getInstance().getDisturbancesSince(new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(14)));
                for (API.Disturbance d : disturbances) {
                    items.add(new DisturbanceRecyclerViewAdapter.DisturbanceItem(d, networks));
                }
            } catch (APIException e) {
                return false;
            }
            Collections.sort(items, Collections.<DisturbanceRecyclerViewAdapter.DisturbanceItem>reverseOrder(new Comparator<DisturbanceRecyclerViewAdapter.DisturbanceItem>() {
                @Override
                public int compare(DisturbanceRecyclerViewAdapter.DisturbanceItem disturbanceItem, DisturbanceRecyclerViewAdapter.DisturbanceItem t1) {
                    return Long.valueOf(disturbanceItem.startTime.getTime()).compareTo(Long.valueOf(t1.startTime.getTime()));
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
                recyclerView.setAdapter(new DisturbanceRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                /*updateInformationView.setText(R.string.frag_lines_error_updating);*/
            }
            progressBar.setVisibility(View.GONE);
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
    public interface OnListFragmentInteractionListener extends OnTopFragmentInteractionListener {
        void onListFragmentInteraction(DisturbanceRecyclerViewAdapter.DisturbanceItem item);

        MainService getLocationService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_LOCATION_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    new DisturbanceFragment.UpdateDataTask().execute(context);
                    break;
            }
        }
    };
}
