package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class LineFragment extends Fragment {

    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;
    private RecyclerView recyclerView = null;
    private ProgressBar progressBar = null;
    private TextView updateInformationView = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public LineFragment() {
    }

    public static LineFragment newInstance(int columnCount) {
        LineFragment fragment = new LineFragment();
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
        View view = inflater.inflate(R.layout.fragment_line_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.setVisibility(View.GONE);
        progressBar = (ProgressBar) view.findViewById(R.id.loading_indicator);
        progressBar.setVisibility(View.VISIBLE);
        updateInformationView = (TextView) view.findViewById(R.id.update_information);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_LOCATION_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);
        if (mListener != null && mListener.getLocationService() != null) {
            new UpdateDataTask().execute(context);
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

    private final String CACHE_FILENAME = "LineFragmentCache";

    private static class CachedInfo implements Serializable {
        public List<LineRecyclerViewAdapter.LineItem> items;
        public Date updated;
    }

    private void cacheLineItems(Context context, CachedInfo info) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), CACHE_FILENAME));
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(info);
            os.close();
            fos.close();
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            e.printStackTrace();
        }
    }

    private CachedInfo readLineItemsCache(Context context) {
        CachedInfo info = null;
        try {
            FileInputStream fis = new FileInputStream(new File(context.getCacheDir(), CACHE_FILENAME));
            ObjectInputStream is = new ObjectInputStream(fis);
            info = (CachedInfo) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            // oh well, we'll have to do without cache
            // caching is best-effort
            e.printStackTrace();
        }
        return info;
    }

    private void showCachedItems(Context context) {
        CachedInfo info = readLineItemsCache(context);
        if (info != null) {
            recyclerView.setAdapter(new LineRecyclerViewAdapter(info.items, mListener));
            recyclerView.invalidate();
            recyclerView.setVisibility(View.VISIBLE);
            updateInformationView.setText(String.format(getString(R.string.frag_lines_updated),
                    DateUtils.getRelativeTimeSpanString(context, info.updated.getTime(), true)));
        }
    }

    private class UpdateDataTask extends AsyncTask<Context, Integer, Boolean> {
        private Context context;
        private List<LineRecyclerViewAdapter.LineItem> items = new ArrayList<>();

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
            try {
                List<API.Disturbance> disturbances = API.getInstance().getOngoingDisturbances();
                for (Line l : lines) {
                    boolean foundDisturbance = false;
                    for (API.Disturbance d : disturbances) {
                        if (d.line.equals(l.getId()) && !d.ended) {
                            foundDisturbance = true;
                            items.add(new LineRecyclerViewAdapter.LineItem(l, new Date(d.startTime[0] * 1000)));
                            break;
                        }
                    }
                    if (!foundDisturbance) {
                        items.add(new LineRecyclerViewAdapter.LineItem(l));
                    }
                }
            } catch (APIException e) {
                return false;
            }
            Collections.sort(items, new Comparator<LineRecyclerViewAdapter.LineItem>() {
                @Override
                public int compare(LineRecyclerViewAdapter.LineItem lineItem, LineRecyclerViewAdapter.LineItem t1) {
                    return lineItem.name.compareTo(t1.name);
                }
            });
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
                recyclerView.setAdapter(new LineRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                recyclerView.setVisibility(View.VISIBLE);
                updateInformationView.setText(String.format(getString(R.string.frag_lines_updated),
                        DateUtils.getRelativeTimeSpanString(this.context, (new Date()).getTime(), true)));
                CachedInfo info = new CachedInfo();
                info.items = items;
                info.updated = new Date();
                cacheLineItems(context, info);
            } else {
                updateInformationView.setText(R.string.frag_lines_error_updating);
                showCachedItems(context);
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
    public interface OnListFragmentInteractionListener {
        void onListFragmentInteraction(LineRecyclerViewAdapter.LineItem item);

        MainService getLocationService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_LOCATION_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    new UpdateDataTask().execute(context);
                    break;
            }
        }
    };
}
