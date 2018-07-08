package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.BuildConfig;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.ui.widget.DatasetInfoView;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.subway.Network;

public class AboutFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;
    private LinearLayout networksLayout;

    public AboutFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment AboutFragment.
     */
    public static AboutFragment newInstance() {
        AboutFragment fragment = new AboutFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public boolean needsTopology() {
        return false;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_about;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_about";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_about_title), false, false);
        setHasOptionsMenu(true);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        ImageView segvaultLogo = (ImageView) view.findViewById(R.id.segvault_logo_view);
        segvaultLogo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.developer_website)));
                startActivity(browserIntent);
            }
        });

        networksLayout = (LinearLayout) view.findViewById(R.id.about_networks);
        refreshDatasetInfo();

        TextView versionView = (TextView) view.findViewById(R.id.about_version);
        versionView.setText(String.format(getResources().getString(R.string.frag_about_version),
                String.format("%s #%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)));

        ListView thirdPartyList = (ListView) view.findViewById(R.id.about_thirdparty);

        List<Map<String, String>> data = new ArrayList<>();
        Map<String, String> jgrapht = new HashMap<>(3);
        jgrapht.put("name", "JGraphT");
        jgrapht.put("license", "© Barak Naveh and Contributors - Eclipse Public License");
        jgrapht.put("url", "http://jgrapht.org");
        data.add(jgrapht);

        Map<String, String> jackson = new HashMap<>(3);
        jackson.put("name", "Jackson JSON");
        jackson.put("license", "© FasterXML LLC - Apache License 2.0");
        jackson.put("url", "https://github.com/FasterXML/jackson-core");
        data.add(jackson);

        Map<String, String> mpack = new HashMap<>(3);
        mpack.put("name", "MessagePack for Java");
        mpack.put("license", "© msgpack.org - Apache License 2.0");
        mpack.put("url", "https://github.com/msgpack/msgpack-java/");
        data.add(mpack);

        Map<String, String> ajob = new HashMap<>(3);
        ajob.put("name", "Android-Job");
        ajob.put("license", "© Evernote Corporation - Apache License 2.0");
        ajob.put("url", "https://evernote.github.io/android-job/");
        data.add(ajob);

        Map<String, String> mpref = new HashMap<>(3);
        mpref.put("name", "Material Preference");
        mpref.put("license", "© consp1racy - Apache License 2.0");
        mpref.put("url", "https://github.com/consp1racy/android-support-preference");
        data.add(mpref);

        Map<String, String> jss = new HashMap<>(3);
        jss.put("name", "java-string-similarity");
        jss.put("license", "© Thibault Debatty - MIT License");
        jss.put("url", "https://github.com/tdebatty/java-string-similarity");
        data.add(jss);

        Map<String, String> realm = new HashMap<>(3);
        realm.put("name", "Realm Java");
        realm.put("license", "© Realm Inc - Apache License 2.0");
        realm.put("url", "https://realm.io/products/realm-mobile-database/");
        data.add(realm);

        Map<String, String> htmltv = new HashMap<>(3);
        htmltv.put("name", "HtmlTextView");
        htmltv.put("license", "© Sufficiently Secure - Apache License 2.0");
        htmltv.put("url", "https://github.com/SufficientlySecure/html-textview/");
        data.add(htmltv);

        Map<String, String> intro = new HashMap<>(3);
        intro.put("name", "AppIntro");
        intro.put("license", "© Paolo Rotolo, Maximilian Narr - Apache License 2.0");
        intro.put("url", "https://github.com/apl-devs/AppIntro");
        data.add(intro);

        Map<String, String> mtp = new HashMap<>(3);
        mtp.put("name", "Material Tap Target Prompt");
        mtp.put("license", "© Samuel Wall - Apache License 2.0");
        mtp.put("url", "https://github.com/sjwall/MaterialTapTargetPrompt");
        data.add(mtp);

        Map<String, String> pickers = new HashMap<>(3);
        pickers.put("name", "android-betterpickers");
        pickers.put("license", "© Derek Brameyer, Code-Troopers - Apache License 2.0");
        pickers.put("url", "https://github.com/code-troopers/android-betterpickers");
        data.add(pickers);

        Map<String, String> mlct = new HashMap<>(3);
        mlct.put("name", "multiline-collapsingtoolbar");
        mlct.put("license", "© Johan von Forstner, Raphael Michel - Apache License 2.0");
        mlct.put("url", "https://github.com/opacapp/multiline-collapsingtoolbar");
        data.add(mlct);

        Map<String, String> licon = new HashMap<>(3);
        licon.put("name", "Line icons");
        licon.put("license", "Rdg Vito @ Wikipedia - CC-BY-SA 3.0");
        licon.put("url", "https://commons.wikimedia.org/wiki/File:MetroLisboa-linha-amarela.svg");
        data.add(licon);

        Map<String, String> aosp = new HashMap<>(2);
        aosp.put("name", "AOSP");
        aosp.put("license", getString(R.string.frag_about_aosp_legal));
        data.add(aosp);

        SimpleAdapter adapter = new SimpleAdapter(getContext(), data,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "license"},
                new int[]{android.R.id.text1, android.R.id.text2});

        thirdPartyList.setAdapter(adapter);

        thirdPartyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String url = ((Map<String, String>) parent.getItemAtPosition(position)).get("url");
                if (url != null) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                }
            }
        });

        thirdPartyList.setFocusable(false); // avoid having the view scroll to the list on load
        thirdPartyList.setOnTouchListener(new View.OnTouchListener() {
            // Setting on Touch Listener for handling the touch inside ScrollView
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
        setListViewHeightBasedOnChildren(thirdPartyList);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.about, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_qr_code) {
            ImageView image = new ImageView(getContext());
            image.setImageResource(R.drawable.ic_gplay_qr_code);

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(getContext()).
                            setMessage(R.string.frag_about_qrcode_desc).
                            setPositiveButton(R.string.frag_about_qrcode_close, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).
                            setView(image);
            builder.create().show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        Collection<Network> getNetworks();

        void updateNetworks(String... network_ids);

        void cacheAllExtras(String... network_ids);
    }

    public void updateNetworks(String... network_ids) {
        if (mListener != null) {
            mListener.updateNetworks(network_ids);
        }
    }

    public void cacheAllExtras(String... network_ids) {
        if (mListener != null) {
            mListener.cacheAllExtras(network_ids);
        }
    }

    private void refreshDatasetInfo() {
        if (mListener != null) {
            networksLayout.removeAllViews();
            for (Network n : mListener.getNetworks()) {
                DatasetInfoView d = new DatasetInfoView(getContext(), n, this);
                networksLayout.addView(d);
            }
        }
    }

    /**** Method for Setting the Height of the ListView dynamically.
     **** Hack to fix the issue of not showing all the items of the ListView
     **** when placed inside a ScrollView  ****/
    private static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null)
            return;

        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        int totalHeight = 0;
        View view = null;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            view = listAdapter.getView(i, view, listView);
            if (i == 0)
                view.setLayoutParams(new ViewGroup.LayoutParams(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT));

            view.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    refreshDatasetInfo();
                    break;
            }
        }
    };
}
