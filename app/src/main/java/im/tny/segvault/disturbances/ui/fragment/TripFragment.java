package im.tny.segvault.disturbances.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.lang.ref.WeakReference;
import java.util.List;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.activity.TripCorrectionActivity;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Transfer;

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
    private Trip trip;

    private View rootView;
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
        rootView = inflater.inflate(R.layout.fragment_trip, container, false);

        layoutRoute = rootView.findViewById(R.id.layout_route);

        stationNamesView = rootView.findViewById(R.id.station_names_view);
        dateView = rootView.findViewById(R.id.date_view);
        correctButton = rootView.findViewById(R.id.correct_button);
        deleteButton = rootView.findViewById(R.id.delete_button);
        statsLayout = rootView.findViewById(R.id.layout_stats);
        statsView = rootView.findViewById(R.id.stats_view);

        network = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);

        correctButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), TripCorrectionActivity.class);
            intent.putExtra(TripCorrectionActivity.EXTRA_NETWORK_ID, networkId);
            intent.putExtra(TripCorrectionActivity.EXTRA_TRIP_ID, tripId);
            startActivity(intent);
        });

        deleteButton.setOnClickListener(view1 -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.frag_trip_delete_confirm_title)
                    .setMessage(R.string.frag_trip_delete_confirm_desc)
                    .setPositiveButton(R.string.frag_trip_delete, (dialog, which) -> new DeleteTripTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        // do nothing
                    })
                    .show();
        });
        this.inflater = inflater;
        new LoadTripTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // always expand sheet on landscape as otherwise it only shows the title and it's awkward
            BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from((View) rootView.getParent());
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        new LoadTripTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
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
            stepview.setOnClickListener(view -> {
                Intent intent = new Intent(context, StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                context.startActivity(intent);
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

                centerLineStripeLayout.setBackground(gd);
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
            stepview.setOnClickListener(view -> {
                Intent intent = new Intent(context, StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                context.startActivity(intent);
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
        stepview.setOnClickListener(view -> {
            Intent intent = new Intent(context, StationActivity.class);
            intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
            intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
            context.startActivity(intent);
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

    private static class LoadTripTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<TripFragment> parentRef;
        private Path path;
        private boolean canBeCorrected;

        LoadTripTask(TripFragment parent) {
            this.parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            TripFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();
            parent.trip = db.tripDao().get(parent.tripId);

            if (parent.trip == null) {
                return false;
            }

            canBeCorrected = parent.trip.canBeCorrected(db);

            path = parent.trip.toConnectionPath(db, parent.network);
            return path != null;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                TripFragment parent = parentRef.get();
                if (parent == null) {
                    return;
                }

                Station origin = path.getStartVertex().getStation();
                Station dest = path.getEndVertex().getStation();

                SpannableStringBuilder builder = new SpannableStringBuilder();
                if (path.getEdgeList().size() == 0) {
                    builder.append("#");
                    builder.setSpan(new ImageSpan(parent.getActivity(), R.drawable.ic_beenhere_black_24dp),
                            builder.length() - 1, builder.length(), 0);
                    builder.append(" " + origin.getName());
                } else {
                    builder.append(origin.getName() + " ").append("#");
                    builder.setSpan(new ImageSpan(parent.getActivity(), R.drawable.ic_arrow_forward_black_24dp),
                            builder.length() - 1, builder.length(), 0);
                    builder.append(" " + dest.getName());

                }
                parent.stationNamesView.setText(builder);

                parent.dateView.setText(
                        DateUtils.formatDateTime(parent.getContext(),
                                path.getEntryTime(0).getTime(),
                                DateUtils.FORMAT_SHOW_DATE));

                int length = path.getTimeablePhysicalLength();
                long time = path.getMovementMilliseconds();
                if (length == 0 || time == 0) {
                    parent.statsLayout.setVisibility(View.GONE);
                } else {
                    parent.statsView.setText(String.format(parent.getString(R.string.frag_trip_stats),
                            length,
                            DateUtils.formatElapsedTime(time / 1000),
                            (((double) length / (double) (time / 1000)) * 3.6)));
                }

                populatePathView(parent.getContext(), parent.inflater, path, parent.layoutRoute, true);

                if (!canBeCorrected) {
                    parent.correctButton.setVisibility(View.GONE);
                }
            }
        }
    }

    private static class DeleteTripTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<TripFragment> parentRef;

        DeleteTripTask(TripFragment parent) {
            this.parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            TripFragment parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent.getContext()).getDB();
            db.tripDao().delete(parent.trip);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                TripFragment parent = parentRef.get();
                if (parent != null) {
                    parent.dismiss();
                }
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
    public interface OnFragmentInteractionListener {
    }
}

