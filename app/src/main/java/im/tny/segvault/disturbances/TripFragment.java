package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;
import io.realm.Realm;

/**
 * Created by gabriel on 5/10/17.
 */

public class TripFragment extends BottomSheetDialogFragment {
    private TripFragment.OnFragmentInteractionListener mListener;

    private static final String ARG_TRIP_ID = "tripId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String tripId;
    private String networkId;

    private TextView stationNamesView;

    private LinearLayout layoutRoute;

    public TripFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment TripFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static TripFragment newInstance(String networkId, String tripId) {
        TripFragment fragment = new TripFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        args.putString(ARG_TRIP_ID, tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tripId = getArguments().getString(ARG_TRIP_ID);
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_trip, container, false);

        layoutRoute = (LinearLayout) view.findViewById(R.id.layout_route);

        stationNamesView = (TextView) view.findViewById(R.id.station_names_view);

        if (mListener == null)
            return view;
        MainService service = mListener.getMainService();
        if (service == null)
            return view;

        Network network = service.getNetwork(networkId);

        Realm realm = Realm.getDefaultInstance();
        Trip trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();

        Station origin = network.getStation(trip.getPath().get(0).getStation().getId());
        Station dest = network.getStation(trip.getPath().get(trip.getPath().size() - 1).getStation().getId());

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(origin.getName() + " ").append("#");
        builder.setSpan(new ImageSpan(getActivity(), R.drawable.ic_arrow_forward_black_24dp),
                builder.length() - 1, builder.length(), 0);
        builder.append(" " + dest.getName());
        stationNamesView.setText(builder);

        TextView dateView = (TextView)view.findViewById(R.id.date_view);
        dateView.setText(
                DateUtils.formatDateTime(getContext(),
                        trip.getPath().get(0).getEntryDate().getTime(),
                        DateUtils.FORMAT_SHOW_DATE));

        List<Connection> el = trip.toConnectionPath(network);

        layoutRoute.removeAllViews();

        boolean isFirst = true;
        int curBulletIdx = 0;
        for (int i = 0; i < el.size(); i++) {
            Connection c = el.get(i);
            if (i == 0 && c instanceof Transfer) {
                // starting with a line change? ignore
                continue;
            }
            if (isFirst) {
                View stepview = inflater.inflate(R.layout.path_station_initial, layoutRoute, false);

                Line line = c.getSource().getLine();

                int lineColor = line.getColor();
                FrameLayout lineStripeLayout = (FrameLayout) stepview.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                timeView.setText(
                        DateUtils.formatDateTime(getContext(),
                                trip.getPath().get(curBulletIdx).getEntryDate().getTime(),
                                DateUtils.FORMAT_SHOW_TIME));

                RouteFragment.populateStationView(getActivity(), network, c.getSource(), stepview);

                layoutRoute.addView(stepview);
                curBulletIdx++;
                isFirst = false;
            } else {
                Line targetLine = c.getTarget().getLine();

                View stepview = inflater.inflate(R.layout.path_station_intermediate, layoutRoute, false);

                int prevLineColor = c.getSource().getLine().getColor();
                FrameLayout prevLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setBackgroundColor(prevLineColor);

                int nextLineColor = targetLine.getColor();
                FrameLayout nextLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                timeView.setText(
                        DateUtils.formatDateTime(getContext(),
                                trip.getPath().get(curBulletIdx).getEntryDate().getTime(),
                                DateUtils.FORMAT_SHOW_TIME));

                RouteFragment.populateStationView(getActivity(), network, c.getSource(), stepview);

                layoutRoute.addView(stepview);
                curBulletIdx++;

                if (c instanceof Transfer && i != el.size() - 1) {
                    c = el.get(++i);
                }
                if (i == el.size() - 1) {
                    stepview = inflater.inflate(R.layout.path_station_final, layoutRoute, false);

                    int lineColor = c.getSource().getLine().getColor();
                    FrameLayout lineStripeLayout = (FrameLayout) stepview.findViewById(R.id.line_stripe_layout);
                    lineStripeLayout.setBackgroundColor(lineColor);

                    timeView = (TextView) stepview.findViewById(R.id.time_view);
                    timeView.setText(
                            DateUtils.formatDateTime(getContext(),
                                    trip.getPath().get(curBulletIdx).getEntryDate().getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));

                    RouteFragment.populateStationView(getActivity(), network, c.getTarget(), stepview);

                    layoutRoute.addView(stepview);
                    curBulletIdx++;
                }
            }
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof TripFragment.OnFragmentInteractionListener) {
            mListener = (TripFragment.OnFragmentInteractionListener) context;
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
}

