package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Transfer;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RouteFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link RouteFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RouteFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;

    public RouteFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment RouteFragment.
     */
    public static RouteFragment newInstance() {
        RouteFragment fragment = new RouteFragment();
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

    private Network network = null;
    private StationPickerView originPicker;
    private StationPickerView destinationPicker;
    private ImageButton swapButton;

    private LinearLayout layoutRoute;
    private LinearLayout layoutInstructions;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_route_title), R.id.nav_plan_route, false, false);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        layoutRoute = (LinearLayout) view.findViewById(R.id.layout_route);
        layoutInstructions = (LinearLayout) view.findViewById(R.id.layout_instructions);
        swapButton = (ImageButton) view.findViewById(R.id.swap_button);
        swapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Station o = originPicker.getSelection();
                originPicker.setSelection(destinationPicker.getSelection());
                destinationPicker.setSelection(o);
                tryPlanRoute();
            }
        });

        originPicker = (StationPickerView) view.findViewById(R.id.origin_picker);
        destinationPicker = (StationPickerView) view.findViewById(R.id.destination_picker);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_LOCATION_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        if (mListener != null && mListener.getMainService() != null) {
            network = mListener.getMainService().getNetwork("pt-ml");
            // the network map might not be loaded yet
            if (network != null) {
                populatePickers(network);
            }
        }
        return view;
    }

    private void populatePickers(Network network) {
        List<Station> stations = new ArrayList<>(network.vertexSet());

        originPicker.setStations(stations);
        originPicker.setOnStationSelectedListener(new StationPickerView.OnStationSelectedListener() {
            @Override
            public void onStationSelected(Station station) {
                destinationPicker.focusOnEntry();
                tryPlanRoute();
            }
        });
        originPicker.setOnSelectionLostListener(new StationPickerView.OnSelectionLostListener() {
            @Override
            public void onSelectionLost() {
                hideRoute();
            }
        });

        destinationPicker.setStations(stations);
        destinationPicker.setOnStationSelectedListener(new StationPickerView.OnStationSelectedListener() {
            @Override
            public void onStationSelected(Station station) {
                tryPlanRoute();
                destinationPicker.clearFocus();
                // Check if no view has focus:
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        });
        destinationPicker.setOnSelectionLostListener(new StationPickerView.OnSelectionLostListener() {
            @Override
            public void onSelectionLost() {
                hideRoute();
            }
        });
    }

    private void tryPlanRoute() {
        if (originPicker.getSelection() == null || destinationPicker.getSelection() == null) {
            // not enough information
            return;
        }

        AStarShortestPath as = new AStarShortestPath(network);
        AStarAdmissibleHeuristic heuristic = new AStarAdmissibleHeuristic<Station>() {
            @Override
            public double getCostEstimate(Station sourceVertex, Station targetVertex) {
                return 0;
            }
        };
        Station source = originPicker.getSelection();
        // hackish "annotations" for the connection weighter
        source.putMeta("is_route_source", true);
        Station target = destinationPicker.getSelection();
        target.putMeta("is_route_target", true);

        GraphPath gp = as.getShortestPath(source, target, heuristic);
        source.putMeta("is_route_source", null);
        target.putMeta("is_route_target", null);
        showRoute(gp);
    }

    private void hideRoute() {
        layoutRoute.setVisibility(View.GONE);
        swapButton.setVisibility(View.GONE);
        layoutInstructions.setVisibility(View.VISIBLE);
    }

    private void showRoute(GraphPath path) {
        layoutRoute.removeAllViews();
        List<Connection> el = path.getEdgeList();

        boolean isFirst = true;
        for (int i = 0; i < el.size(); i++) {
            Connection c = el.get(i);
            if (i == 0 && c instanceof Transfer) {
                // starting with a line change? ignore
                continue;
            }
            if (isFirst) {
                View view = getActivity().getLayoutInflater().inflate(R.layout.step_enter_network, layoutRoute, false);

                Line line = c.getSource().getLines().get(0);

                int lineColor = line.getColor();
                FrameLayout lineStripeLayout = (FrameLayout) view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                populateStationView(c.getSource(), view);

                if (c.getSource().hasTransferEdge(network)) {
                    Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(line.getId()));
                    drawable.setColorFilter(lineColor, PorterDuff.Mode.SRC_ATOP);

                    FrameLayout iconFrame = (FrameLayout) view.findViewById(R.id.frame_icon);
                    if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        iconFrame.setBackgroundDrawable(drawable);
                    } else {
                        iconFrame.setBackground(drawable);
                    }

                    TextView lineView = (TextView) view.findViewById(R.id.line_name_view);
                    lineView.setText(String.format(getString(R.string.frag_route_line_name), line.getName()));
                    lineView.setTextColor(lineColor);

                    LinearLayout lineLayout = (LinearLayout) view.findViewById(R.id.line_layout);
                    lineLayout.setVisibility(View.VISIBLE);
                }

                TextView directionView = (TextView) view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                c.getTarget().getDirectionForConnection(c).getName())));

                if (line.getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                if (mListener != null && mListener.getMainService() != null) {
                    Map<String, MainService.LineStatus> statuses = mListener.getMainService().getLineStatus();
                    if (statuses.get(line.getId()) != null &&
                            statuses.get(line.getId()).down) {
                        LinearLayout disturbancesWarningLayout = (LinearLayout) view.findViewById(R.id.disturbances_warning_layout);
                        disturbancesWarningLayout.setVisibility(View.VISIBLE);
                    }
                }

                layoutRoute.addView(view);
                isFirst = false;
            }

            if (i == el.size() - 1) {
                View view = getActivity().getLayoutInflater().inflate(R.layout.step_exit_network, layoutRoute, false);

                int lineColor = c.getSource().getLines().get(0).getColor();
                FrameLayout lineStripeLayout = (FrameLayout) view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                populateStationView(c.getTarget(), view);

                layoutRoute.addView(view);
            } else if (c instanceof Transfer) {
                Connection c2 = el.get(i + 1);

                Line targetLine = c.getTarget().getLines().get(0);

                View view = getActivity().getLayoutInflater().inflate(R.layout.step_change_line, layoutRoute, false);

                int prevLineColor = c.getSource().getLines().get(0).getColor();
                FrameLayout prevLineStripeLayout = (FrameLayout) view.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setBackgroundColor(prevLineColor);

                int nextLineColor = targetLine.getColor();
                FrameLayout nextLineStripeLayout = (FrameLayout) view.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                populateStationView(c.getSource(), view);

                Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(targetLine.getId()));
                drawable.setColorFilter(nextLineColor, PorterDuff.Mode.SRC_ATOP);

                FrameLayout iconFrame = (FrameLayout) view.findViewById(R.id.frame_icon);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    iconFrame.setBackgroundDrawable(drawable);
                } else {
                    iconFrame.setBackground(drawable);
                }

                TextView lineView = (TextView) view.findViewById(R.id.line_name_view);
                lineView.setText(String.format(getString(R.string.frag_route_line_name), targetLine.getName()));
                lineView.setTextColor(nextLineColor);

                LinearLayout lineLayout = (LinearLayout) view.findViewById(R.id.line_layout);
                lineLayout.setVisibility(View.VISIBLE);

                TextView directionView = (TextView) view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                c2.getTarget().getDirectionForConnection(c2).getName())));

                if (targetLine.getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                if (mListener != null && mListener.getMainService() != null) {
                    Map<String, MainService.LineStatus> statuses = mListener.getMainService().getLineStatus();
                    if (statuses.get(targetLine.getId()) != null &&
                            statuses.get(targetLine.getId()).down) {
                        LinearLayout disturbancesWarningLayout = (LinearLayout) view.findViewById(R.id.disturbances_warning_layout);
                        disturbancesWarningLayout.setVisibility(View.VISIBLE);
                    }
                }

                layoutRoute.addView(view);
            }
        }
        if (layoutRoute.getChildCount() == 0) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.step_already_there, layoutRoute, false);

            populateStationView(originPicker.getSelection(), view);

            if (originPicker.getSelection().getLines().get(0).getUsualCarCount() < network.getUsualCarCount() ||
                    destinationPicker.getSelection().getLines().get(0).getUsualCarCount() < network.getUsualCarCount()) {
                LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                carsWarningLayout.setVisibility(View.VISIBLE);
            }

            layoutRoute.addView(view);
        }

        layoutInstructions.setVisibility(View.GONE);
        layoutRoute.setVisibility(View.VISIBLE);
        swapButton.setVisibility(View.VISIBLE);
    }

    private void populateStationView(Station station, View view) {
        TextView stationView = (TextView) view.findViewById(R.id.station_view);
        stationView.setText(station.getName());

        View separatorView = (View) view.findViewById(R.id.feature_separator_view);

        ImageView liftView = (ImageView) view.findViewById(R.id.feature_lift_view);
        if (station.getFeatures().lift) {
            liftView.setVisibility(View.VISIBLE);
            separatorView.setVisibility(View.VISIBLE);
        }

        ImageView busView = (ImageView) view.findViewById(R.id.feature_bus_view);
        if (station.getFeatures().bus) {
            busView.setVisibility(View.VISIBLE);
            separatorView.setVisibility(View.VISIBLE);
        }

        ImageView boatView = (ImageView) view.findViewById(R.id.feature_boat_view);
        if (station.getFeatures().boat) {
            boatView.setVisibility(View.VISIBLE);
            separatorView.setVisibility(View.VISIBLE);
        }

        ImageView trainView = (ImageView) view.findViewById(R.id.feature_train_view);
        if (station.getFeatures().train) {
            trainView.setVisibility(View.VISIBLE);
            separatorView.setVisibility(View.VISIBLE);
        }

        ImageView airportView = (ImageView) view.findViewById(R.id.feature_airport_view);
        if (station.getFeatures().airport) {
            airportView.setVisibility(View.VISIBLE);
            separatorView.setVisibility(View.VISIBLE);
        }
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("origin_focused", originPicker.isFocused());
        outState.putBoolean("destination_focused", destinationPicker.isFocused());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("origin_focused", false)) {
                originPicker.focusOnEntry();
            }
            if (savedInstanceState.getBoolean("destination_focused", false)) {
                destinationPicker.focusOnEntry();
            }
            tryPlanRoute();
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
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_LOCATION_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    if (mListener != null) {
                        network = mListener.getMainService().getNetwork("pt-ml");
                        // the network map might not be loaded yet
                        if (network != null) {
                            populatePickers(network);
                        }
                    }
                    break;
            }
        }
    };
}
