package im.tny.segvault.disturbances;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
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
    private Button deleteButton;

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
        deleteButton = (Button) view.findViewById(R.id.delete_button);

        if (mListener == null)
            return view;
        MainService service = mListener.getMainService();
        if (service == null)
            return view;

        Network network = service.getNetwork(networkId);

        Realm realm = Realm.getDefaultInstance();

        Trip trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();
        Path path = trip.toConnectionPath(network);

        Station origin = path.getStartVertex().getStation();
        Station dest = path.getEndVertex().getStation();

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(origin.getName() + " ").append("#");
        builder.setSpan(new ImageSpan(getActivity(), R.drawable.ic_arrow_forward_black_24dp),
                builder.length() - 1, builder.length(), 0);
        builder.append(" " + dest.getName());
        stationNamesView.setText(builder);

        TextView dateView = (TextView) view.findViewById(R.id.date_view);
        dateView.setText(
                DateUtils.formatDateTime(getContext(),
                        path.getEntryTime(0).getTime(),
                        DateUtils.FORMAT_SHOW_DATE));

        populatePathView(getContext(), inflater, network, path, layoutRoute);

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.frag_trip_delete_confirm_title)
                        .setMessage(R.string.frag_trip_delete_confirm_desc)
                        .setPositiveButton(R.string.frag_trip_delete, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteTrip();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .show();
            }
        });
        return view;
    }

    public static void populatePathView(final Context context, final LayoutInflater inflater, final Network network, final Path path, ViewGroup root) {
        root.removeAllViews();

        List<Connection> el = path.getEdgeList();

        if (el.size() == 0) {
            View stepview = inflater.inflate(R.layout.path_station_initial, root, false);

            FrameLayout lineStripeLayout = (FrameLayout) stepview.findViewById(R.id.line_stripe_layout);
            lineStripeLayout.setVisibility(View.INVISIBLE);

            TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
            if (path.getManualEntry(0)) {
                timeView.setVisibility(View.INVISIBLE);
            } else {
                timeView.setText(
                        DateUtils.formatDateTime(context,
                                path.getEntryTime(0).getTime(),
                                DateUtils.FORMAT_SHOW_TIME));
            }

            RouteFragment.populateStationView(context, network, path.getStartVertex(), stepview);

            root.addView(stepview);
            return;
        }

        boolean isFirst = true;
        for (int i = 0; i < el.size(); i++) {
            Connection c = el.get(i);
            if (i == 0 && c instanceof Transfer) {
                // starting with a line change? ignore
                continue;
            }
            if (isFirst) {
                View stepview = inflater.inflate(R.layout.path_station_initial, root, false);

                Line line = c.getSource().getLine();

                int lineColor = line.getColor();
                FrameLayout lineStripeLayout = (FrameLayout) stepview.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                if (path.getManualEntry(i)) {
                    timeView.setVisibility(View.INVISIBLE);
                } else {
                    timeView.setText(
                            DateUtils.formatDateTime(context,
                                    path.getEntryTime(i).getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));
                }

                RouteFragment.populateStationView(context, network, c.getSource(), stepview);

                root.addView(stepview);
                isFirst = false;
            } else {
                Line targetLine = c.getTarget().getLine();

                View stepview = inflater.inflate(R.layout.path_station_intermediate, root, false);

                int prevLineColor = c.getSource().getLine().getColor();
                FrameLayout prevLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setBackgroundColor(prevLineColor);

                int nextLineColor = targetLine.getColor();
                FrameLayout nextLineStripeLayout = (FrameLayout) stepview.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                // left side of the outer && is a small hack to fix time not displaying when the
                // start of the path was extended with the first step getting replaced by a transfer
                if (path.getManualEntry(i) && !(c instanceof Transfer && !path.getManualEntry(i+1))) {
                    timeView.setVisibility(View.INVISIBLE);
                } else {
                    timeView.setText(
                            DateUtils.formatDateTime(context,
                                    path.getEntryTime(i).getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));
                }

                RouteFragment.populateStationView(context, network, c.getSource(), stepview);

                root.addView(stepview);
            }
            if (c instanceof Transfer && i != el.size() - 1) {
                c = el.get(++i);
            }
            if (i == el.size() - 1) {
                View stepview = inflater.inflate(R.layout.path_station_final, root, false);

                int lineColor = c.getSource().getLine().getColor();
                FrameLayout lineStripeLayout = (FrameLayout) stepview.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                TextView timeView = (TextView) stepview.findViewById(R.id.time_view);
                if (path.getManualEntry(i + 1)) {
                    timeView.setVisibility(View.INVISIBLE);
                } else {
                    timeView.setText(
                            DateUtils.formatDateTime(context,
                                    path.getEntryTime(i + 1).getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));
                }

                RouteFragment.populateStationView(context, network, c.getTarget(), stepview);

                root.addView(stepview);
            }

        }

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

    private void deleteTrip() {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Trip trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();
        trip.getPath().deleteAllFromRealm();
        trip.deleteFromRealm();
        realm.commitTransaction();
        dismiss();
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

