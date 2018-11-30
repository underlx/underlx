package im.tny.segvault.disturbances.ui.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.activity.TripCorrectionActivity;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
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

    private Network network;

    private TextView stationNamesView;
    private TextView dateView;
    private Button correctButton;
    private Button deleteButton;

    private LinearLayout statsLayout;
    private TextView statsView;

    private LinearLayout layoutRoute;

    private LayoutInflater inflater;

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

        layoutRoute = view.findViewById(R.id.layout_route);

        stationNamesView = view.findViewById(R.id.station_names_view);
        dateView = view.findViewById(R.id.date_view);
        correctButton = view.findViewById(R.id.correct_button);
        deleteButton = view.findViewById(R.id.delete_button);
        statsLayout = view.findViewById(R.id.layout_stats);
        statsView = view.findViewById(R.id.stats_view);

        network = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);

        correctButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), TripCorrectionActivity.class);
                intent.putExtra(TripCorrectionActivity.EXTRA_NETWORK_ID, networkId);
                intent.putExtra(TripCorrectionActivity.EXTRA_TRIP_ID, tripId);
                startActivity(intent);
            }
        });

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
        this.inflater = inflater;
        refreshUI();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshUI();
    }

    private void refreshUI() {
        Network network = this.network;
        if (network == null) {
            return;
        }
        Realm realm = Application.getDefaultRealmInstance(getContext());
        Trip trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();

        if(trip == null) {
            return;
        }

        Path path = trip.toConnectionPath(network);

        Station origin = path.getStartVertex().getStation();
        Station dest = path.getEndVertex().getStation();

        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (path.getEdgeList().size() == 0) {
            builder.append("#");
            builder.setSpan(new ImageSpan(getActivity(), R.drawable.ic_beenhere_black_24dp),
                    builder.length() - 1, builder.length(), 0);
            builder.append(" " + origin.getName());
        } else {
            builder.append(origin.getName() + " ").append("#");
            builder.setSpan(new ImageSpan(getActivity(), R.drawable.ic_arrow_forward_black_24dp),
                    builder.length() - 1, builder.length(), 0);
            builder.append(" " + dest.getName());

        }
        stationNamesView.setText(builder);

        dateView.setText(
                DateUtils.formatDateTime(getContext(),
                        path.getEntryTime(0).getTime(),
                        DateUtils.FORMAT_SHOW_DATE));

        int length = path.getTimeablePhysicalLength();
        long time = path.getMovementMilliseconds();
        if (length == 0 || time == 0) {
            statsLayout.setVisibility(View.GONE);
        } else {
            statsView.setText(String.format(getString(R.string.frag_trip_stats),
                    length,
                    DateUtils.formatElapsedTime(time / 1000),
                    (((double) length / (double) (time / 1000)) * 3.6)));
        }

        populatePathView(getContext(), inflater, path, layoutRoute, true);

        if (!trip.canBeCorrected()) {
            correctButton.setVisibility(View.GONE);
        }

        realm.close();
    }

    public static void populatePathView(final Context context, final LayoutInflater inflater, final Path path, ViewGroup root, boolean showInfoIcons) {
        root.removeAllViews();

        List<Connection> el = path.getEdgeList();

        final int stepviewPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());

        if (el.size() == 0) {
            View stepview = inflater.inflate(R.layout.path_station, root, false);

            FrameLayout prevLineStripeLayout = stepview.findViewById(R.id.prev_line_stripe_layout);
            FrameLayout nextLineStripeLayout = stepview.findViewById(R.id.next_line_stripe_layout);
            prevLineStripeLayout.setVisibility(View.INVISIBLE);
            nextLineStripeLayout.setVisibility(View.INVISIBLE);
            FrameLayout centerLineStripeLayout = stepview.findViewById(R.id.center_line_stripe_layout);
            centerLineStripeLayout.setVisibility(View.VISIBLE);

            TextView timeView = stepview.findViewById(R.id.time_view);
            if (path.getManualEntry(0)) {
                timeView.setVisibility(View.INVISIBLE);
            } else {
                timeView.setText(
                        DateUtils.formatDateTime(context,
                                path.getEntryTime(0).getTime(),
                                DateUtils.FORMAT_SHOW_TIME));
            }

            final Station station = path.getStartVertex().getStation();
            stepview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(context, StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                    context.startActivity(intent);
                }
            });
            Drawable gd = Util.getColoredDrawableResource(context, R.drawable.station_line_single, station.getLines().get(0).getColor());
            if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                centerLineStripeLayout.setBackgroundDrawable(gd);
            } else {
                centerLineStripeLayout.setBackground(gd);
            }

            RouteFragment.populateStationView(context, station, stepview, showInfoIcons, false);
            stepview.setPadding(stepviewPadding, 0, stepviewPadding, 0);
            root.addView(stepview);
            return;
        }

        boolean isFirst = true;
        Connection c = el.get(0);
        int i = 0;
        for (; i < el.size(); i++) {
            c = el.get(i);
            if (i == 0 && c instanceof Transfer) {
                // starting with a line change? ignore
                continue;
            }

            View stepview = inflater.inflate(R.layout.path_station, root, false);

            FrameLayout prevLineStripeLayout = stepview.findViewById(R.id.prev_line_stripe_layout);
            FrameLayout centerLineStripeLayout = stepview.findViewById(R.id.center_line_stripe_layout);
            centerLineStripeLayout.setVisibility(View.VISIBLE);
            FrameLayout nextLineStripeLayout = stepview.findViewById(R.id.next_line_stripe_layout);

            if (isFirst) {
                Line line = c.getSource().getLine();

                int lineColor = line.getColor();

                prevLineStripeLayout.setVisibility(View.INVISIBLE);
                Drawable background = Util.getColoredDrawableResource(context, R.drawable.station_line_top, lineColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    centerLineStripeLayout.setBackground(background);
                } else {
                    centerLineStripeLayout.setBackgroundDrawable(background);
                }
                centerLineStripeLayout.setVisibility(View.VISIBLE);
                nextLineStripeLayout.setBackgroundColor(lineColor);

                TextView timeView = stepview.findViewById(R.id.time_view);
                if (path.getManualEntry(i)) {
                    timeView.setVisibility(View.INVISIBLE);
                } else {
                    timeView.setText(
                            DateUtils.formatDateTime(context,
                                    path.getEntryTime(i).getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));
                }

                isFirst = false;
            } else {
                Line targetLine = c.getTarget().getLine();

                int prevLineColor = c.getSource().getLine().getColor();
                int nextLineColor = targetLine.getColor();
                prevLineStripeLayout.setBackgroundColor(prevLineColor);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{prevLineColor, nextLineColor});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    centerLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    centerLineStripeLayout.setBackground(gd);
                }
                centerLineStripeLayout.setVisibility(View.VISIBLE);

                TextView timeView = stepview.findViewById(R.id.time_view);
                // left side of the outer && is a small hack to fix time not displaying when the
                // start of the path was extended with the first step getting replaced by a transfer
                if (path.getManualEntry(i) && !(c instanceof Transfer && !path.getManualEntry(i + 1))) {
                    timeView.setVisibility(View.INVISIBLE);
                } else {
                    timeView.setText(
                            DateUtils.formatDateTime(context,
                                    path.getEntryTime(i).getTime(),
                                    DateUtils.FORMAT_SHOW_TIME));
                }
            }

            final Station station = c.getSource().getStation();
            stepview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(context, StationActivity.class);
                    intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                    intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                    context.startActivity(intent);
                }
            });

            RouteFragment.populateStationView(context, station, stepview, showInfoIcons, false);

            stepview.setPadding(stepviewPadding, 0, stepviewPadding, 0);
            root.addView(stepview);

            if (c instanceof Transfer && i != el.size() - 1) {
                c = el.get(++i);
            }
        }
        View stepview = inflater.inflate(R.layout.path_station, root, false);

        int lineColor = c.getSource().getLine().getColor();
        FrameLayout prevLineStripeLayout = stepview.findViewById(R.id.prev_line_stripe_layout);
        FrameLayout centerLineStripeLayout = stepview.findViewById(R.id.center_line_stripe_layout);
        FrameLayout nextLineStripeLayout = stepview.findViewById(R.id.next_line_stripe_layout);

        prevLineStripeLayout.setBackgroundColor(lineColor);
        Drawable background = Util.getColoredDrawableResource(context, R.drawable.station_line_bottom, lineColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            centerLineStripeLayout.setBackground(background);
        } else {
            centerLineStripeLayout.setBackgroundDrawable(background);
        }
        centerLineStripeLayout.setVisibility(View.VISIBLE);
        nextLineStripeLayout.setVisibility(View.INVISIBLE);

        TextView timeView = stepview.findViewById(R.id.time_view);
        if (path.getManualEntry(i)) {
            timeView.setVisibility(View.INVISIBLE);
        } else {
            timeView.setText(
                    DateUtils.formatDateTime(context,
                            path.getEntryTime(i).getTime(),
                            DateUtils.FORMAT_SHOW_TIME));
        }

        final Station station = c.getTarget().getStation();
        stepview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                context.startActivity(intent);
            }
        });

        RouteFragment.populateStationView(context, station, stepview, showInfoIcons, false);
        stepview.setPadding(stepviewPadding, 0, stepviewPadding, 0);
        root.addView(stepview);
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
        Realm realm = Application.getDefaultRealmInstance(getContext());
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                Trip trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();
                if (trip != null) {
                    trip.getPath().deleteAllFromRealm();
                    trip.deleteFromRealm();
                }
            }
        });
        realm.close();
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
    }
}

