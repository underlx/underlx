package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Station;


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

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance() {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }*/
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_map_title), R.id.nav_map, false, false);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        WebView webview = (WebView) view.findViewById(R.id.webview);
        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webview.addJavascriptInterface(new MapWebInterface(this.getContext()), "android");
        webview.loadUrl("file:///android_asset/map.html");

        return view;
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

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public class MapWebInterface {
        Context mContext;
        ObjectMapper mapper = new ObjectMapper();

        public class MapStation {
            public String id;
            public String name;
            public String line;

            public MapStation(String id, String name, String line) {
                this.id = id; this.name = name; this.line = line;
            }
        }

        public class MapConnection {
            public String from;
            public String to;
            public String line;

            public MapConnection(String from, String to, String line) {
                this.from = from; this.to = to; this.line = line;
            }
        }

        public class MapGraph {
            public List<MapStation> stations;
            public List<MapConnection> connections;
        }

        /** Instantiate the interface and set the context */
        MapWebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public String getGraph(String network) {
            if(mListener == null) {
                return "";
            }

            MapGraph g = new MapGraph();
            g.stations = new ArrayList<>();
            g.connections = new ArrayList<>();
            for(Station s : mListener.getMainService().getNetwork(network).vertexSet()) {
                g.stations.add(new MapStation(s.getId(), s.getName(), s.getLines().get(0).getId()));
            }

            List<MapStation> connections = new ArrayList<>();
            for(Connection c : mListener.getMainService().getNetwork(network).edgeSet()) {
                String line = "";
                if(c.getSource().getLines().containsAll(c.getTarget().getLines())) {
                    line = c.getSource().getLines().get(0).getId();
                }
                g.connections.add(new MapConnection(c.getSource().getId(), c.getTarget().getId(), line));
            }

            try {
                return mapper.writeValueAsString(g);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return "";
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
