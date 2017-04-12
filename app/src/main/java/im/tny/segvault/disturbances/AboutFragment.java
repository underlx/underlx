package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
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

import im.tny.segvault.subway.Network;

public class AboutFragment extends Fragment {
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mListener != null) {
            mListener.setActionBarTitle(getString(R.string.frag_about_title));
        }
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        networksLayout = (LinearLayout) view.findViewById(R.id.about_networks);
        if (mListener != null) {
            for (Network n : mListener.getNetworks()) {
                DatasetInfoView d = new DatasetInfoView(getContext(), n);
                networksLayout.addView(d);
            }
        }

        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();


        TextView versionView = (TextView) view.findViewById(R.id.about_version);
        versionView.setText(String.format(getResources().getString(R.string.frag_about_version), BuildConfig.VERSION_NAME));

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

        Map<String, String> aosp = new HashMap<>(2);
        aosp.put("name", "AOSP");
        aosp.put("license", "This software contains code derived from code developed by The Android Open Source Project");
        data.add(aosp);

        SimpleAdapter adapter = new SimpleAdapter(getContext(), data,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "license"},
                new int[]{android.R.id.text1, android.R.id.text2});

        thirdPartyList.setAdapter(adapter);

        thirdPartyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String url = ((Map<String, String>)parent.getItemAtPosition(position)).get("url");
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
        return view;
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

    public interface OnFragmentInteractionListener {
        void setActionBarTitle(String title);

        Collection<Network> getNetworks();
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
}
