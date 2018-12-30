package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TableRow;
import android.widget.TextView;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Transformation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.CacheManager;
import im.tny.segvault.disturbances.Connectivity;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.util.SimpleDividerItemDecoration;
import im.tny.segvault.disturbances.ui.activity.StatsActivity;
import im.tny.segvault.disturbances.ui.adapter.TripRecyclerViewAdapter;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.subway.Network;
import io.realm.Realm;

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

    private TableRow posPlayLoadingRow;
    private TableRow posPlayRow1;
    private TableRow posPlayRow2;
    private ImageView posPlayAvatarView;
    private TextView posPlayUserView;
    private TextView posPlayLevelView;
    private ProgressBar posPlayLevelProgress;
    private TextView posPlayNextLevelView;
    private TextView posPlayOverallView;
    private TextView posPlayWeekView;

    private boolean showVisits = false;
    private Menu menu;

    private Realm realm = Application.getDefaultRealmInstance(getContext());

    /**
     * Mandatory constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TripHistoryFragment() {

    }

    @Override
    public boolean needsTopology() {
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_trip_history;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_trip_history";
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
        setUpActivity(getString(R.string.frag_trip_history_title), false, false);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_trip_history_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        emptyView = view.findViewById(R.id.no_trips_view);
        tripCountView = view.findViewById(R.id.trip_count_view);
        tripTotalLengthView = view.findViewById(R.id.trip_total_length_view);
        tripTotalTimeView = view.findViewById(R.id.trip_total_time_view);
        tripAverageSpeedView = view.findViewById(R.id.trip_average_speed_view);

        posPlayLoadingRow = view.findViewById(R.id.posplay_loading_row);
        posPlayRow1 = view.findViewById(R.id.posplay_row1);
        posPlayRow2 = view.findViewById(R.id.posplay_row2);
        posPlayAvatarView = view.findViewById(R.id.posplay_avatar_view);
        posPlayUserView = view.findViewById(R.id.posplay_user_view);
        posPlayLevelView = view.findViewById(R.id.posplay_level_view);
        posPlayLevelProgress = view.findViewById(R.id.posplay_level_progress);
        posPlayNextLevelView = view.findViewById(R.id.posplay_next_level_view);
        posPlayOverallView = view.findViewById(R.id.posplay_overall_view);
        posPlayWeekView = view.findViewById(R.id.posplay_week_view);

        Drawable progressDrawable = posPlayLevelProgress.getProgressDrawable().mutate();
        progressDrawable.setColorFilter(Color.parseColor("#0078E7"), android.graphics.PorterDuff.Mode.SRC_IN);
        posPlayLevelProgress.setProgressDrawable(progressDrawable);

        posPlayRow1.setOnClickListener(view1 -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.posplay_website)));
            startActivity(browserIntent);
        });

        recyclerView = view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(context));

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        view.findViewById(R.id.stats_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), StatsActivity.class);
            startActivity(intent);
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_TRIP_REALM_UPDATED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.registerReceiver(mBroadcastReceiver, filter);

        new TripHistoryFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.trip_history, menu);
        if (showVisits) {
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
            getSwipeRefreshLayout().setRefreshing(true);
        }

        protected Boolean doInBackground(Void... v) {
            while (mListener == null || mListener.getMainService() == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            Collection<Network> networks = Coordinator.get(getContext()).getMapManager().getNetworks();
            Realm realm = Application.getDefaultRealmInstance(getContext());
            for (Trip t : realm.where(Trip.class).findAll()) {
                TripRecyclerViewAdapter.TripItem item = new TripRecyclerViewAdapter.TripItem(t, networks);
                if (!item.isVisit) {
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
            Collections.sort(items, Collections.<TripRecyclerViewAdapter.TripItem>reverseOrder((tripItem, t1) -> Long.valueOf(tripItem.originTime.getTime()).compareTo(Long.valueOf(t1.originTime.getTime()))));
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            if (!isAdded()) {
                // prevent onPostExecute from doing anything if no longer attached to an activity
                return;
            }
            tripCountView.setText(String.format("%d", tripCount));
            tripTotalLengthView.setText(String.format(getString(R.string.frag_trip_history_length_value), (double) tripTotalLength / 1000f));

            long days = tripTotalTime / TimeUnit.DAYS.toMillis(1);
            long hours = (tripTotalTime % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1);
            long minutes = (tripTotalTime % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
            if (days == 0) {
                tripTotalTimeView.setText(String.format(getString(R.string.frag_trip_history_duration_no_days), hours, minutes));
            } else {
                tripTotalTimeView.setText(String.format(getString(R.string.frag_trip_history_duration_with_days), days, hours, minutes));
            }
            tripAverageSpeedView.setText("--");
            if (recyclerView != null && mListener != null) {
                recyclerView.setAdapter(new TripRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                if (result) {
                    emptyView.setVisibility(View.GONE);

                    if (tripTotalMovementTime > 0) {
                        tripAverageSpeedView.setText(String.format(getString(R.string.frag_trip_history_speed_value),
                                ((double) tripTotalTimeableLength / (double) (tripTotalMovementTime / 1000)) * 3.6));
                    }
                } else {
                    emptyView.setVisibility(View.VISIBLE);
                }
            } else {
                emptyView.setVisibility(View.VISIBLE);
            }
            new UpdatePosPlayTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            getSwipeRefreshLayout().setRefreshing(false);
        }
    }

    private final static String POSPLAY_CACHE_KEY = "PosPlayStatus";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PosPlayStatus implements Serializable {
        public String serviceName;

        public long discordID;
        public String username;
        public String avatarURL;
        public int level;
        public float levelProgress;
        public int xp;
        public int xpThisWeek;
        public int rank;
        public int rankThisWeek;
    }

    private class UpdatePosPlayTask extends AsyncTask<Void, Void, Boolean> {
        private PosPlayStatus status;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            posPlayLoadingRow.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            CacheManager cm = Coordinator.get(getContext()).getCacheManager();

            status = cm.fetchOrGet(POSPLAY_CACHE_KEY, (key, storeDate) -> false, key -> {
                try {
                    List<API.PairConnection> connections = API.getInstance().getPairConnections();
                    for (API.PairConnection connection : connections) {
                        if (connection.service.equals("posplay")) {
                            PosPlayStatus status = API.getInstance().getMapper().convertValue(connection.extra, PosPlayStatus.class);
                            if (status != null) {
                                status.serviceName = connection.serviceName;
                            }
                            return status;
                        }
                    }
                    // if we got here, there's no PosPlay connection (device may have been unpaired)
                    // so delete key to ensure we don't show PosPlay info anymore
                    cm.remove(POSPLAY_CACHE_KEY);
                } catch (IllegalArgumentException e) { // convertValue throws
                    return null;
                } catch (APIException e) {
                    return null;
                }
                return null;
            }, PosPlayStatus.class);
            return status != null;
        }

        protected void onPostExecute(Boolean result) {
            posPlayLoadingRow.setVisibility(View.GONE);
            if (result) {
                posPlayRow1.setVisibility(View.VISIBLE);
                posPlayRow2.setVisibility(View.VISIBLE);

                String userLine = String.format(getString(R.string.frag_trip_history_posplay_username), status.username, status.serviceName);
                int nStart = userLine.indexOf(status.username);
                int nEnd = nStart + status.username.length();
                SpannableString userSpannable = new SpannableString(userLine);
                userSpannable.setSpan(new StyleSpan(Typeface.BOLD), nStart, nEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                posPlayUserView.setText(userSpannable);

                String levelAlone = String.format("%d", status.level);
                String levelLine = String.format(getString(R.string.frag_trip_history_posplay_level), status.level);
                int lStart = levelLine.indexOf(levelAlone);
                int lEnd = lStart + levelAlone.length();
                SpannableString levelSpannable = new SpannableString(levelLine);
                levelSpannable.setSpan(new StyleSpan(Typeface.BOLD), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                posPlayLevelView.setText(levelSpannable);

                posPlayLevelProgress.setProgress(Math.round(status.levelProgress));
                posPlayNextLevelView.setText(String.format("%d", status.level + 1));

                // all-time info
                String placeLine;
                if (status.rank == 0) {
                    placeLine = getString(R.string.frag_trip_history_posplay_no_participation);
                } else if (Util.getCurrentLanguage(getContext()).equals("en")) {
                    placeLine = String.format(getString(R.string.frag_trip_history_posplay_xp_place_english),
                            status.xp, status.rank, Util.getOrdinalSuffix(status.rank));
                } else {
                    placeLine = String.format(getString(R.string.frag_trip_history_posplay_xp_place), status.xp, status.rank);
                }
                Spannable placeSpannable = new SpannableString(placeLine);
                if (status.rank == 1) {
                    placeSpannable.setSpan(new ForegroundColorSpan(Color.parseColor("#DAA520")), placeLine.indexOf("\n"), placeLine.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                posPlayOverallView.setText(placeSpannable);

                // week info
                if (status.rankThisWeek == 0) {
                    placeLine = getString(R.string.frag_trip_history_posplay_no_participation);
                } else if (Util.getCurrentLanguage(getContext()).equals("en")) {
                    placeLine = String.format(getString(R.string.frag_trip_history_posplay_xp_place_english),
                            status.xpThisWeek, status.rankThisWeek, Util.getOrdinalSuffix(status.rankThisWeek));
                } else {
                    placeLine = String.format(getString(R.string.frag_trip_history_posplay_xp_place), status.xpThisWeek, status.rankThisWeek);
                }
                placeSpannable = new SpannableString(placeLine);
                if (status.rankThisWeek == 1) {
                    placeSpannable.setSpan(new ForegroundColorSpan(Color.parseColor("#DAA520")), placeLine.indexOf("\n"), placeLine.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                posPlayWeekView.setText(placeSpannable);

                RequestCreator rc = Picasso.get().load(status.avatarURL);
                if (!Connectivity.isConnectedWifi(getContext())) {
                    rc.networkPolicy(NetworkPolicy.OFFLINE);
                }
                rc.transform(new CircleTransform()).into(posPlayAvatarView);
            } else {
                posPlayRow1.setVisibility(View.GONE);
                posPlayRow2.setVisibility(View.GONE);
            }
        }
    }

    public class CircleTransform implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());

            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;

            Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
            if (squaredBitmap != source) {
                source.recycle();
            }

            Bitmap bitmap = Bitmap.createBitmap(size, size, source.getConfig());

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            BitmapShader shader = new BitmapShader(squaredBitmap,
                    BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setAntiAlias(true);

            float r = size / 2f;
            canvas.drawCircle(r, r, r, paint);

            squaredBitmap.recycle();
            return bitmap;
        }

        @Override
        public String key() {
            return "circle";
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
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                case MainService.ACTION_TRIP_REALM_UPDATED:
                    if (getActivity() != null) {
                        new UpdateDataTask().execute();
                    }
                    break;
            }
        }
    };
}
