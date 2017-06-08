package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link StationLobbyFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link StationLobbyFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StationLobbyFragment extends Fragment {
    private static final String ARG_STATION_ID = "stationId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String stationId;
    private String networkId;

    private OnFragmentInteractionListener mListener;

    private LinearLayout lobbiesLayout;

    public StationLobbyFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param networkId Network ID
     * @param stationId Station ID
     * @return A new instance of fragment StationLobbyFragment.
     */
    public static StationLobbyFragment newInstance(String networkId, String stationId) {
        StationLobbyFragment fragment = new StationLobbyFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        args.putString(ARG_STATION_ID, stationId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stationId = getArguments().getString(ARG_STATION_ID);
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_station_lobby, container, false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(StationActivity.ACTION_MAIN_SERVICE_BOUND);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        lobbiesLayout = (LinearLayout) view.findViewById(R.id.lobbies_layout);

        update();

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

    private void update() {
        if (mListener == null)
            return;
        MainService service = mListener.getMainService();
        if (service == null)
            return;

        Network net = service.getNetwork(networkId);
        Station station = net.getStation(stationId);

        // Lobbies
        for (Lobby lobby : station.getLobbies()) {
            LobbyView v = new LobbyView(getContext(), lobby);
            lobbiesLayout.addView(v);
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
    public interface OnFragmentInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case StationActivity.ACTION_MAIN_SERVICE_BOUND:
                    if (mListener != null) {
                        update();
                    }
                    break;
            }
        }
    };
}
