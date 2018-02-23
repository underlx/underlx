package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.fasterxml.jackson.databind.ObjectMapper;

import im.tny.segvault.subway.Network;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

import static android.content.Context.MODE_PRIVATE;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MapFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;

    private WebView webview;

    private static final String ARG_NETWORK_ID = "networkId";

    private String networkId;
    private boolean portraitMap = false;

    private boolean mockLocationMode = false;

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance(String networkId) {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_map_title), R.id.nav_map, false, false);
        setHasOptionsMenu(true);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        webview = (WebView) view.findViewById(R.id.webview);
        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webview.addJavascriptInterface(new MapWebInterface(this.getContext()), "android");

        webview.getSettings().setLoadWithOverviewMode(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setDisplayZoomControls(false);
        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        portraitMap = sharedPref.getBoolean(PreferenceNames.PortraitMap, false);
        if (portraitMap) {
            webview.getSettings().setUseWideViewPort(false);
            webview.loadUrl("file:///android_asset/map-" + networkId + "-portrait.html");
        } else {
            webview.getSettings().setUseWideViewPort(true);
            webview.loadUrl("file:///android_asset/map-" + networkId + ".html");
        }
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.map, menu);

        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        if (sharedPref != null) {
            if (BuildConfig.DEBUG && sharedPref.getBoolean(PreferenceNames.DeveloperMode, false)) {
                menu.findItem(R.id.menu_mock_location).setVisible(true);
            }
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                showTargetPrompt();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_zoom_out:
                webview.zoomOut();
                return true;
            case R.id.menu_zoom_in:
                webview.zoomIn();
                return true;
            case R.id.menu_swap_map:
                portraitMap = !portraitMap;
                SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
                SharedPreferences.Editor e = sharedPref.edit();
                e.putBoolean(PreferenceNames.PortraitMap, portraitMap);
                e.apply();
                if (portraitMap) {
                    webview.getSettings().setUseWideViewPort(false);
                    webview.loadUrl("file:///android_asset/map-" + networkId + "-portrait.html");
                } else {
                    webview.getSettings().setUseWideViewPort(true);
                    webview.loadUrl("file:///android_asset/map-" + networkId + ".html");
                }
                return true;
            case R.id.menu_mock_location:
                mockLocationMode = !item.isChecked();
                item.setChecked(mockLocationMode);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    private void showTargetPrompt() {
        Context context = getContext();
        boolean isFirstOpen = false;
        if (context != null) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
            if (sharedPref != null) {
                isFirstOpen = sharedPref.getBoolean("fuse_first_map_open", true);
            }
        }

        if (!isFirstOpen) {
            return;
        }

        new MaterialTapTargetPrompt.Builder(getActivity())
                .setTarget(R.id.menu_swap_map)
                .setPrimaryText(R.string.frag_map_switch_type_taptarget_title)
                .setSecondaryText(R.string.frag_map_switch_type_taptarget_subtitle)
                .setPromptStateChangeListener(new MaterialTapTargetPrompt.PromptStateChangeListener() {
                    @Override
                    public void onPromptStateChanged(MaterialTapTargetPrompt prompt, int state) {
                        if (state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                            // User has pressed the prompt target
                            Context context = getContext();
                            if (context != null) {
                                SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
                                SharedPreferences.Editor e = sharedPref.edit();
                                e.putBoolean("fuse_first_map_open", false);
                                e.apply();
                            }
                        }
                    }
                })
                .setFocalColour(ContextCompat.getColor(getContext(), R.color.colorAccent))
                .setBackgroundColour(ContextCompat.getColor(getContext(), R.color.colorPrimaryLight))
                .show();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public class MapWebInterface {
        Context mContext;
        ObjectMapper mapper = new ObjectMapper();

        /**
         * Instantiate the interface and set the context
         */
        MapWebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onStationClicked(String id) {
            if (mockLocationMode) {
                if (mListener == null)
                    return;
                MainService service = mListener.getMainService();
                if (service == null)
                    return;
                Network net = service.getNetwork(networkId);
                if (net != null) {
                    service.mockLocation(net.getStation(id));
                }
            } else {
                Intent intent = new Intent(getContext(), StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, id);
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, networkId);
                startActivity(intent);
            }
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
    }
}
