package im.tny.segvault.disturbances.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.CacheManager;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.util.RichTextUtils;

import static android.content.Context.MODE_PRIVATE;

public class HomeBackersFragment extends Fragment {
    private OnFragmentInteractionListener mListener;
    private HtmlTextView contentView = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public HomeBackersFragment() {
    }

    public static HomeBackersFragment newInstance() {
        HomeBackersFragment fragment = new HomeBackersFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_backers, container, false);

        contentView = view.findViewById(R.id.content_view);
        return view;
    }

    private void setContent(String html) {
        contentView.setHtml(html);
        contentView.setText(RichTextUtils.replaceAll((Spanned) contentView.getText(), URLSpan.class, new RichTextUtils.URLSpanConverter(), new RichTextUtils.ClickSpan.OnClickListener() {
            @Override
            public void onClick(String url) {
                if (mListener == null) {
                    return;
                }
                if (url.startsWith("help:")) {
                    mListener.onHelpLinkClicked(url.substring(5));
                } else if (url.startsWith("station:")) {
                    mListener.onStationLinkClicked(url.substring(8));
                } else if (url.startsWith("mailto:")) {
                    mListener.onMailtoLinkClicked(url.substring(7));
                } else {
                    mListener.onLinkClicked(url);
                }
            }
        }));
    }

    private static class RetrieveBackersTask extends AsyncTask<Void, String, String> implements
            CacheManager.ItemStaleChecker,
            CacheManager.ItemFetcher {
        private HomeBackersFragment top;
        private String locale;
        private Date latestContentDate;

        RetrieveBackersTask(HomeBackersFragment top, String locale) {
            this.top = top;
            this.locale = locale;
        }

        @Override
        protected String doInBackground(Void... voids) {
            CacheManager cm = Coordinator.get(top.getContext()).getCacheManager();
            String html = cm.get(buildCacheKey(), String.class);
            publishProgress(html);

            SharedPreferences sharedPref = top.getContext().getSharedPreferences("settings", MODE_PRIVATE);
            if (html != null &&
                    new Date().getTime() - sharedPref.getLong(PreferenceNames.LastBackersHTMLUpdateCheck, 0) < TimeUnit.DAYS.toMillis(1)) {
                return null;
            }

            try {
                latestContentDate = API.getInstance().getBackersLastModified(locale);
            } catch (APIException e) {
                return null;
            }

            // update check successful
            SharedPreferences.Editor e = sharedPref.edit();
            e.putLong(PreferenceNames.LastBackersHTMLUpdateCheck, new Date().getTime());
            e.apply();

            return cm.getOrFetch(buildCacheKey(), this, this, String.class);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values.length == 0 || values[0] == null || !top.isAdded()) {
                return;
            }
            top.setContent(values[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null || !top.isAdded()) {
                return;
            }
            top.setContent(result);
        }

        @Override
        public Serializable fetchItemData(String key) {
            try {
                return API.getInstance().getBackers(locale);
            } catch (APIException e) {
                return null;
            }
        }

        @Override
        public boolean isItemStale(String key, Date storeDate) {
            return latestContentDate != null && latestContentDate.getTime() > storeDate.getTime();
        }

        private static final String BACKERS_CACHE_KEY = "BackersHTML-%s";

        private String buildCacheKey() {
            return String.format(BACKERS_CACHE_KEY, locale);
        }
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

        new RetrieveBackersTask(this, Util.getCurrentLanguage(getContext())).execute();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        void onHelpLinkClicked(String destination);

        void onStationLinkClicked(String destination);

        void onMailtoLinkClicked(String address);

        void onLinkClicked(String destination);
    }
}