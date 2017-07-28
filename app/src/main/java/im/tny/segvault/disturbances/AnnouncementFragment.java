package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import im.tny.segvault.subway.Network;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class AnnouncementFragment extends TopFragment {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;

    private static final String ARG_NETWORK_ID = "networkId";
    private String networkId;

    private OnListFragmentInteractionListener mListener;

    private RecyclerView recyclerView = null;
    private TextView emptyView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AnnouncementFragment() {
    }

    @SuppressWarnings("unused")
    public static AnnouncementFragment newInstance(String networkId, int columnCount) {
        AnnouncementFragment fragment = new AnnouncementFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        args.putString(ARG_NETWORK_ID, networkId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_announcements_title), R.id.nav_announcements, false, true);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_announcement_list, container, false);

        // Set the adapter
        Context context = view.getContext();
        emptyView = (TextView) view.findViewById(R.id.no_announcements_view);
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }

        // fix scroll fling. less than ideal, but apparently there's still no other solution
        recyclerView.setNestedScrollingEnabled(false);

        getSwipeRefreshLayout().setRefreshing(true);

        new AnnouncementFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        getSwipeRefreshLayout().setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new AnnouncementFragment.UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
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
            if (!Connectivity.isConnected(getContext())) {
                return false;
            }
            if (mListener == null || mListener.getMainService() == null) {
                return false;
            }

            Network net = mListener.getMainService().getNetwork(networkId);

            if (net == null) {
                return false;
            }

            try {
                URL url = new URL(net.getAnnouncementsURL());
                InputStream inputStream = url.openConnection().getInputStream();
                items = parseFeed(inputStream);
            } catch (IOException ex) {
                return false;
            } catch (XmlPullParserException e) {
                return false;
            }
            Collections.sort(items, Collections.reverseOrder(new Comparator<AnnouncementRecyclerViewAdapter.AnnouncementItem>() {
                @Override
                public int compare(AnnouncementRecyclerViewAdapter.AnnouncementItem announcementItem, AnnouncementRecyclerViewAdapter.AnnouncementItem t1) {
                    return Long.valueOf(announcementItem.pubDate.getTime()).compareTo(Long.valueOf(t1.pubDate.getTime()));
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
            if (result && recyclerView != null && mListener != null && items.size() > 0) {
                recyclerView.setAdapter(new AnnouncementRecyclerViewAdapter(items, mListener));
                recyclerView.invalidate();
                emptyView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.VISIBLE);
            }
            getSwipeRefreshLayout().setRefreshing(false);
            if (!initialRefresh) {
                if (result) {
                    Snackbar.make(getFloatingActionButton(), R.string.frag_announcements_updated, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(getFloatingActionButton(), R.string.error_no_connection, Snackbar.LENGTH_SHORT)
                            .setAction(getString(R.string.error_no_connection_action_retry), new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    new AnnouncementFragment.UpdateDataTask().executeOnExecutor(THREAD_POOL_EXECUTOR);
                                }
                            }).show();
                }
            } else {
                initialRefresh = false;
            }
        }

        private List<AnnouncementRecyclerViewAdapter.AnnouncementItem> parseFeed(InputStream inputStream) throws XmlPullParserException, IOException {
            String title = null;
            String link = null;
            String description = null;
            String pubDate = null;
            boolean isItem = false;
            List<AnnouncementRecyclerViewAdapter.AnnouncementItem> items = new ArrayList<>();

            try {
                XmlPullParser xmlPullParser = Xml.newPullParser();
                xmlPullParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xmlPullParser.setInput(inputStream, null);

                while (xmlPullParser.next() != XmlPullParser.END_DOCUMENT) {
                    int eventType = xmlPullParser.getEventType();

                    String name = xmlPullParser.getName();
                    if (name == null)
                        continue;

                    if (eventType == XmlPullParser.END_TAG) {
                        if (name.equalsIgnoreCase("item")) {
                            isItem = false;
                        }
                        continue;
                    }

                    if (eventType == XmlPullParser.START_TAG) {
                        if (name.equalsIgnoreCase("item")) {
                            isItem = true;
                            continue;
                        }
                    }

                    String result = "";
                    if (xmlPullParser.next() == XmlPullParser.TEXT) {
                        result = xmlPullParser.getText();
                        xmlPullParser.nextTag();
                    }

                    if (name.equalsIgnoreCase("title")) {
                        title = result;
                    } else if (name.equalsIgnoreCase("link")) {
                        link = result;
                    } else if (name.equalsIgnoreCase("description")) {
                        description = result;
                    } else if (name.equalsIgnoreCase("pubDate")) {
                        pubDate = result;
                    }

                    if (title != null && link != null && description != null) {
                        if (isItem && pubDate != null) {
                            DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
                            try {
                                Date date = formatter.parse(pubDate);
                                items.add(new AnnouncementRecyclerViewAdapter.AnnouncementItem(date, title, parseDescription(description), link));
                            } catch (ParseException e) {
                                // oh well, skip this item
                            }
                        }

                        title = null;
                        link = null;
                        description = null;
                        isItem = false;
                    }
                }

                return items;
            } finally {
                inputStream.close();
            }
        }
    }

    public String parseDescription(String rawDescription) {
        String description = "";
        try {
            XmlPullParser xmlPullParser = Xml.newPullParser();
            xmlPullParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xmlPullParser.setInput(new StringReader(rawDescription));

            while (xmlPullParser.next() != XmlPullParser.END_DOCUMENT) {
                int eventType = xmlPullParser.getEventType();

                if (eventType == XmlPullParser.END_TAG) {
                    if (xmlPullParser.getName().equalsIgnoreCase("p")) {
                        // we don't care about other paragraphs
                        break;
                    }
                } else if (eventType == XmlPullParser.START_TAG) {
                    if (xmlPullParser.getName().equalsIgnoreCase("br")) {
                        description += "\n";
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    description += xmlPullParser.getText().trim();
                }
            }
        } catch (Exception ex) {
            return rawDescription;
        }
        return description;
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
