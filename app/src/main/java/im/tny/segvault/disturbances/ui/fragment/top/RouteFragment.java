package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.widget.StationPickerView;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.LineActivity;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.routing.ChangeLineStep;
import im.tny.segvault.s2ls.routing.EnterStep;
import im.tny.segvault.s2ls.routing.ExitStep;
import im.tny.segvault.s2ls.routing.NeutralQualifier;
import im.tny.segvault.s2ls.routing.NeutralWeighter;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.s2ls.routing.Step;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;

import static android.content.Context.MODE_PRIVATE;


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

    private static final String ARG_NETWORK_ID = "networkId";

    private String networkId;

    public RouteFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment RouteFragment.
     */
    public static RouteFragment newInstance(String networkId) {
        RouteFragment fragment = new RouteFragment();
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

    private Network network = null;
    private S2LS loc = null;
    private StationPickerView originPicker;
    private StationPickerView destinationPicker;
    private ImageButton swapButton;
    private Button navigationStartButton;
    private CheckBox useRealtimeCheckbox;
    private TextView routeEtaView;

    private LinearLayout layoutNetworkClosed;
    private TextView viewNetworkClosed;

    private LinearLayout layoutOriginStationClosed;
    private LinearLayout layoutDestinationStationClosed;
    private TextView viewOriginStationClosed;
    private TextView viewDestinationStationClosed;
    private LinearLayout layoutRoute;
    private LinearLayout layoutInstructions;
    private RelativeLayout layoutBottomSheet;

    private Route route = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_route_title), R.id.nav_plan_route, false, false);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        layoutNetworkClosed = (LinearLayout) view.findViewById(R.id.network_closed_layout);
        viewNetworkClosed = (TextView) view.findViewById(R.id.network_closed_view);
        layoutRoute = (LinearLayout) view.findViewById(R.id.layout_route);
        layoutOriginStationClosed = (LinearLayout) view.findViewById(R.id.origin_station_closed_layout);
        layoutDestinationStationClosed = (LinearLayout) view.findViewById(R.id.destination_station_closed_layout);
        viewOriginStationClosed = (TextView) view.findViewById(R.id.origin_station_closed_view);
        viewDestinationStationClosed = (TextView) view.findViewById(R.id.destination_station_closed_view);
        layoutInstructions = (LinearLayout) view.findViewById(R.id.layout_instructions);
        layoutBottomSheet = (RelativeLayout) view.findViewById(R.id.bottom_sheet_layout);
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
        navigationStartButton = (Button) view.findViewById(R.id.navigation_start_button);
        navigationStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                S2LS loc = mListener.getMainService().getS2LS(networkId);
                if (loc != null && route != null) {
                    loc.setCurrentTargetRoute(route, false);
                    switchToPage("nav_home");
                }
            }
        });
        useRealtimeCheckbox = (CheckBox) view.findViewById(R.id.use_realtime_check);
        useRealtimeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                tryPlanRoute();
            }
        });
        routeEtaView = (TextView) view.findViewById(R.id.route_eta_view);

        originPicker = (StationPickerView) view.findViewById(R.id.origin_picker);
        destinationPicker = (StationPickerView) view.findViewById(R.id.destination_picker);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        if (mListener != null && mListener.getMainService() != null) {
            network = mListener.getMainService().getNetwork(networkId);
            loc = mListener.getMainService().getS2LS(networkId);
            // the network map might not be loaded yet
            if (network != null && loc != null) {
                populatePickers();
                updateClosedWarning();
            }
        }
        return view;
    }

    @Override
    public boolean isScrollable() {
        // we handle scrolling ourselves because of bottom sheet
        return false;
    }

    private void populatePickers() {
        List<Station> stations = new ArrayList<>(network.getStations());

        originPicker.setStations(stations);
        originPicker.setAllStationsSortStrategy(new StationPickerView.EnterFrequencySortStrategy());
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
        if(!loc.inNetwork()) {
            originPicker.setAllowMyLocation(true);
        }

        if (loc.getCurrentTrip() != null) {
            originPicker.setWeakSelection(loc.getCurrentTrip().getEndVertex().getStation());
        }

        destinationPicker.setStations(stations);
        destinationPicker.setAllStationsSortStrategy(new StationPickerView.ExitFrequencySortStrategy());
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

        String destId = mListener.getRouteDestination();
        if(destId != null) {
            destinationPicker.setSelectionById(destId);
        }
    }

    private void updateClosedWarning() {
        if (network.isOpen()) {
            layoutNetworkClosed.setVisibility(View.GONE);
        } else {
            Formatter f = new Formatter();
            DateUtils.formatDateRange(getContext(), f, network.getNextOpenTime(), network.getNextOpenTime(), DateUtils.FORMAT_SHOW_TIME, network.getTimezone().getID());
            viewNetworkClosed.setText(String.format(getString(R.string.warning_network_closed), f.toString()));
            layoutNetworkClosed.setVisibility(View.VISIBLE);
        }
    }

    private void tryPlanRoute() {
        if (originPicker.getSelection() == null || destinationPicker.getSelection() == null) {
            // not enough information
            return;
        }

        Route realtimeRoute = Route.calculate(network, originPicker.getSelection(), destinationPicker.getSelection());
        Route neutralRoute = Route.calculate(network, originPicker.getSelection(), destinationPicker.getSelection(),
                new NeutralWeighter(), new NeutralQualifier());
        if(useRealtimeCheckbox.isChecked()) {
            route = realtimeRoute;
        } else {
            route = neutralRoute;
        }

        showRoute(realtimeRoute.getPath().getEdgeList().equals(neutralRoute.getPath().getEdgeList()));
    }

    private void hideRoute() {
        layoutRoute.setVisibility(View.GONE);
        layoutOriginStationClosed.setVisibility(View.GONE);
        layoutDestinationStationClosed.setVisibility(View.GONE);
        swapButton.setVisibility(View.GONE);
        layoutInstructions.setVisibility(View.VISIBLE);
        layoutBottomSheet.setVisibility(View.GONE);
        useRealtimeCheckbox.setVisibility(View.GONE);
    }

    private void showRoute(boolean realtimeEqualsNeutral) {
        if (route == null) {
            return;
        }
        layoutRoute.removeAllViews();
        if (originPicker.getSelection().isAlwaysClosed()) {
            viewOriginStationClosed.setText(String.format(getString(R.string.frag_route_station_closed_extended), originPicker.getSelection().getName()));
            layoutOriginStationClosed.setVisibility(View.VISIBLE);
        } else if (originPicker.getSelection().isExceptionallyClosed(network, new Date()) && useRealtimeCheckbox.isChecked()) {
            viewOriginStationClosed.setText(String.format(getString(R.string.frag_route_station_closed_schedule), originPicker.getSelection().getName()));
            layoutOriginStationClosed.setVisibility(View.VISIBLE);
        } else {
            layoutOriginStationClosed.setVisibility(View.GONE);
        }

        if (destinationPicker.getSelection().isAlwaysClosed()) {
            viewDestinationStationClosed.setText(String.format(getString(R.string.frag_route_station_closed_extended), destinationPicker.getSelection().getName()));
            layoutDestinationStationClosed.setVisibility(View.VISIBLE);
        } else if (destinationPicker.getSelection().isExceptionallyClosed(network, new Date()) && useRealtimeCheckbox.isChecked()) {
            viewDestinationStationClosed.setText(String.format(getString(R.string.frag_route_station_closed_schedule), destinationPicker.getSelection().getName()));
            layoutDestinationStationClosed.setVisibility(View.VISIBLE);
        } else {
            layoutDestinationStationClosed.setVisibility(View.GONE);
        }

        for (Step step : route) {
            View view = null;
            if (step instanceof EnterStep) {
                view = getActivity().getLayoutInflater().inflate(R.layout.step_enter_network, layoutRoute, false);

                final Line line = step.getLine();

                int lineColor = line.getColor();
                FrameLayout lineStripeLayout = (FrameLayout) view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                populateStationView(getActivity(), step.getStation(), view);

                if (step.getStation().getLines().size() > 1) {
                    Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(line.getId()));
                    drawable.setColorFilter(lineColor, PorterDuff.Mode.SRC_ATOP);

                    FrameLayout iconFrame = (FrameLayout) view.findViewById(R.id.frame_icon);
                    if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        iconFrame.setBackgroundDrawable(drawable);
                    } else {
                        iconFrame.setBackground(drawable);
                    }

                    TextView lineView = (TextView) view.findViewById(R.id.line_name_view);
                    lineView.setText(String.format(getString(R.string.frag_route_line_name), Util.getLineNames(getContext(), line)[0]));
                    lineView.setTextColor(lineColor);

                    LinearLayout lineLayout = (LinearLayout) view.findViewById(R.id.line_layout);
                    lineLayout.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(getContext(), LineActivity.class);
                            intent.putExtra(LineActivity.EXTRA_LINE_ID, line.getId());
                            intent.putExtra(LineActivity.EXTRA_NETWORK_ID, network.getId());
                            startActivity(intent);
                        }
                    });
                    lineLayout.setVisibility(View.VISIBLE);
                }

                TextView directionView = (TextView) view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                ((EnterStep) step).getDirection().getName())));

                if (line.getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                if (mListener != null && mListener.getMainService() != null) {
                    Map<String, LineStatusCache.Status> statuses = mListener.getLineStatusCache().getLineStatus();
                    if (statuses.get(line.getId()) != null &&
                            statuses.get(line.getId()).down) {
                        LinearLayout disturbancesWarningLayout = (LinearLayout) view.findViewById(R.id.disturbances_warning_layout);
                        disturbancesWarningLayout.setVisibility(View.VISIBLE);
                    }
                }
            } else if (step instanceof ChangeLineStep) {
                final ChangeLineStep lStep = (ChangeLineStep) step;
                view = getActivity().getLayoutInflater().inflate(R.layout.step_change_line, layoutRoute, false);

                int prevLineColor = lStep.getLine().getColor();
                FrameLayout prevLineStripeLayout = (FrameLayout) view.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setBackgroundColor(prevLineColor);

                int nextLineColor = lStep.getTarget().getColor();
                FrameLayout nextLineStripeLayout = (FrameLayout) view.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                populateStationView(getActivity(), lStep.getStation(), view);

                Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(lStep.getTarget().getId()));
                drawable.setColorFilter(nextLineColor, PorterDuff.Mode.SRC_ATOP);

                FrameLayout iconFrame = (FrameLayout) view.findViewById(R.id.frame_icon);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    iconFrame.setBackgroundDrawable(drawable);
                } else {
                    iconFrame.setBackground(drawable);
                }

                TextView lineView = (TextView) view.findViewById(R.id.line_name_view);
                lineView.setText(String.format(getString(R.string.frag_route_line_name), Util.getLineNames(getContext(), lStep.getTarget())[0]));
                lineView.setTextColor(nextLineColor);

                LinearLayout lineLayout = (LinearLayout) view.findViewById(R.id.line_layout);
                lineLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getContext(), LineActivity.class);
                        intent.putExtra(LineActivity.EXTRA_LINE_ID, lStep.getTarget().getId());
                        intent.putExtra(LineActivity.EXTRA_NETWORK_ID, network.getId());
                        startActivity(intent);
                    }
                });
                lineLayout.setVisibility(View.VISIBLE);

                TextView directionView = (TextView) view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                lStep.getDirection().getName())));

                if (lStep.getTarget().getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                if (mListener != null && mListener.getMainService() != null) {
                    Map<String, LineStatusCache.Status> statuses = mListener.getLineStatusCache().getLineStatus();
                    if (statuses.get(lStep.getTarget().getId()) != null &&
                            statuses.get(lStep.getTarget().getId()).down) {
                        LinearLayout disturbancesWarningLayout = (LinearLayout) view.findViewById(R.id.disturbances_warning_layout);
                        disturbancesWarningLayout.setVisibility(View.VISIBLE);
                    }
                }
            } else if (step instanceof ExitStep) {
                view = getActivity().getLayoutInflater().inflate(R.layout.step_exit_network, layoutRoute, false);

                int lineColor = step.getLine().getColor();
                FrameLayout lineStripeLayout = (FrameLayout) view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                populateStationView(getActivity(), step.getStation(), view);
            }
            layoutRoute.addView(view);
        }

        if (layoutRoute.getChildCount() == 0) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.step_already_there, layoutRoute, false);

            populateStationView(getActivity(), originPicker.getSelection(), view);

            // TODO maybe revive this
            /*if (originPicker.getSelection().getLine().getUsualCarCount() < network.getUsualCarCount() ||
                    destinationPicker.getSelection().getLine().getUsualCarCount() < network.getUsualCarCount()) {
                LinearLayout carsWarningLayout = (LinearLayout) view.findViewById(R.id.cars_warning_layout);
                carsWarningLayout.setVisibility(View.VISIBLE);
            }*/

            layoutRoute.addView(view);
        }

        if (network.isAboutToClose() && route.hasLineChange()) {
            Formatter f = new Formatter();
            DateUtils.formatDateRange(getContext(), f, network.getNextCloseTime(), network.getNextCloseTime(), DateUtils.FORMAT_SHOW_TIME, network.getTimezone().getID());
            viewNetworkClosed.setText(
                    String.format(getString(R.string.warning_network_about_to_close_transfers),
                            f.toString(), destinationPicker.getSelection().getName()));
            layoutNetworkClosed.setVisibility(View.VISIBLE);
        } else updateClosedWarning();

        layoutInstructions.setVisibility(View.GONE);
        layoutRoute.setVisibility(View.VISIBLE);
        swapButton.setVisibility(View.VISIBLE);
        if(realtimeEqualsNeutral) {
            useRealtimeCheckbox.setVisibility(View.GONE);
        } else {
            useRealtimeCheckbox.setVisibility(View.VISIBLE);
        }

        int length = 0;
        double realWeight = 0;
        // TODO: switch to using route.getPath().getWeight() directly once DisturbanceAwareWeighter
        // can actually provide proper weights for lines with disturbances, and not just some
        // extremely large number
        NeutralWeighter weighter = new NeutralWeighter();
        for(Connection c : route.getPath().getEdgeList()) {
            length += c.getWorldLength();
            realWeight += weighter.getEdgeWeight(network, c);
        }
        if (length < 1000) {
            routeEtaView.setText(
                    String.format("%s (%d m)",
                            DateUtils.formatElapsedTime((int) realWeight),
                            length));
        } else {
            routeEtaView.setText(
                    String.format("%s (%.01f km)",
                            DateUtils.formatElapsedTime((int) realWeight),
                            length / 1000.0));
        }

        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        boolean locationEnabled = sharedPref.getBoolean(PreferenceNames.LocationEnable, true);
        if (locationEnabled) {
            layoutBottomSheet.setVisibility(View.VISIBLE);
        }
    }

    public static void populateStationView(final Context context, final Station station, View view, boolean showInfoIcons, boolean setOnClickListener) {
        TextView stationView = (TextView) view.findViewById(R.id.station_view);
        stationView.setText(station.getName());

        if(station.isExceptionallyClosed(station.getNetwork(), new Date())) {
            stationView.setTextColor(Color.GRAY);
        }

        if(showInfoIcons) {
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

            ImageView parkingView = (ImageView) view.findViewById(R.id.feature_parking_view);
            if (station.getFeatures().parking) {
                parkingView.setVisibility(View.VISIBLE);
                separatorView.setVisibility(View.VISIBLE);
            }

            ImageView bikeView = (ImageView) view.findViewById(R.id.feature_bike_view);
            if (station.getFeatures().bike) {
                bikeView.setVisibility(View.VISIBLE);
                separatorView.setVisibility(View.VISIBLE);
            }
        }

        LinearLayout stationLayout = (LinearLayout) view.findViewById(R.id.station_layout);

        if(setOnClickListener) {
            stationLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (context != null) {
                        Intent intent = new Intent(context, StationActivity.class);
                        intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                        intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                        context.startActivity(intent);
                    }
                }
            });
        }
    }

    public static void populateStationView(final Context context, final Station station, View view, boolean showInfoIcons) {
        populateStationView(context, station, view, showInfoIcons, true);
    }

    public static void populateStationView(final Context context, final Station station, View view) {
        populateStationView(context, station, view, true, true);
    }

    public static void populateStationView(final Context context, final Stop stop, View view, boolean showInfoIcons) {
        populateStationView(context, stop.getStation(), view, showInfoIcons, true);
    }

    public static void populateStationView(final Context context, final Stop stop, View view) {
        populateStationView(context, stop.getStation(), view, true, true);
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
        String getRouteDestination();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    if (mListener != null) {
                        network = mListener.getMainService().getNetwork(networkId);
                        loc = mListener.getMainService().getS2LS(networkId);
                        // the network map might not be loaded yet
                        if (network != null && loc != null) {
                            populatePickers();
                            updateClosedWarning();
                        }
                    }
                    break;
            }
        }
    };
}
