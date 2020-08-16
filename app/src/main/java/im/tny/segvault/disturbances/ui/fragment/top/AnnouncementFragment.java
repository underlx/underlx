package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.Context;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Announcement;
import im.tny.segvault.disturbances.ui.adapter.AnnouncementRecyclerViewAdapter;
import im.tny.segvault.disturbances.Connectivity;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class AnnouncementFragment extends TopFragment {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;

    private OnListFragmentInteractionListener mListener;

    private RecyclerView recyclerView = null;
    private TextView emptyView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AnnouncementFragment() {
    }

    @Override
    public boolean needsTopology() {
        return false;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_announcements;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_announcements";
    }

    @SuppressWarnings("unused")
    public static AnnouncementFragment newInstance(int columnCount) {
        AnnouncementFragment fragment = new AnnouncementFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    public static AnnouncementFragment newInstance() {
        return newInstance(0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            if (mColumnCount <= 0) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mColumnCount = 2;
                } else {
                    mColumnCount = 1;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_announcements_title), false, true);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_announcement_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        emptyView = view.findViewById(R.id.no_announcements_view);
        recyclerView = view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        getSwipeRefreshLayout().setRefreshing(true);

        new AnnouncementFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        getSwipeRefreshLayout().setOnRefreshListener(() -> new UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.announcement_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_refresh) {
            new AnnouncementFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    private boolean initialRefresh = true;

    private class UpdateDataTask extends AsyncTask<Void, Integer, Boolean> {
        private List<AnnouncementRecyclerViewAdapter.AnnouncementItem> items = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            getSwipeRefreshLayout().setRefreshing(true);
        }

        protected Boolean doInBackground(Void... v) {
            Context context = getContext();
            if (getActivity() == null || context == null) {
                return false;
            }
            if (!Connectivity.isConnected(context)) {
                return false;
            }
            if (mListener == null || mListener.getMainService() == null) {
                return false;
            }

            try {
                List<API.Announcement> announcements = API.getInstance().getAnnouncements();
                for (API.Announcement a : announcements) {
                    Date date = new Date(a.time[0] * 1000);
                    items.add(new AnnouncementRecyclerViewAdapter.AnnouncementItem(date, a.title, a.body, a.imageURL, a.url, Announcement.getSource(a.source)));
                }
            } catch (APIException e) {
                return false;
            }

            Collections.sort(items, Collections.reverseOrder((announcementItem, t1) -> Long.valueOf(announcementItem.pubDate.getTime()).compareTo(Long.valueOf(t1.pubDate.getTime()))));
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            if (!isAdded()) {
                // prevent onPostExecute from doing anything if no longer attached to an activity
                return;
            }
            if (result && recyclerView != null && mListener != null && items.size() > 0) {
                recyclerView.setAdapter(new AnnouncementRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                if (recyclerView != null) {
                    recyclerView.setVisibility(View.GONE);
                }
                emptyView.setVisibility(View.VISIBLE);
            }
            getSwipeRefreshLayout().setRefreshing(false);
            if (!initialRefresh) {
                if (result) {
                    Snackbar.make(getFloatingActionButton(), R.string.frag_announcements_updated, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(getFloatingActionButton(), R.string.error_no_connection, Snackbar.LENGTH_SHORT)
                            .setAction(getString(R.string.error_no_connection_action_retry), view -> new UpdateDataTask().executeOnExecutor(THREAD_POOL_EXECUTOR)).show();
                }
            } else {
                initialRefresh = false;
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
    public interface OnListFragmentInteractionListener extends OnInteractionListener {
        void onListFragmentInteraction(AnnouncementRecyclerViewAdapter.AnnouncementItem item);

        MainService getMainService();
    }
}
