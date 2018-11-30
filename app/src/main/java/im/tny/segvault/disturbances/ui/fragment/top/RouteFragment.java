package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
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

    @Override
    public boolean needsTopology() {
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_plan_route;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_plan_route";
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
        setUpActivity(getString(R.string.frag_route_title), false, false);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        layoutNetworkClosed = view.findViewById(R.id.network_closed_layout);
        viewNetworkClosed = view.findViewById(R.id.network_closed_view);
        layoutRoute = view.findViewById(R.id.layout_route);
        layoutOriginStationClosed = view.findViewById(R.id.origin_station_closed_layout);
        layoutDestinationStationClosed = view.findViewById(R.id.destination_station_closed_layout);
        viewOriginStationClosed = view.findViewById(R.id.origin_station_closed_view);
        viewDestinationStationClosed = view.findViewById(R.id.destination_station_closed_view);
        layoutInstructions = view.findViewById(R.id.layout_instructions);
        layoutBottomSheet = view.findViewById(R.id.bottom_sheet_layout);
        swapButton = view.findViewById(R.id.swap_button);
        swapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Station o = originPicker.getSelection();
                originPicker.setSelection(destinationPicker.getSelection());
                destinationPicker.setSelection(o);
                tryPlanRoute();
            }
        });
        navigationStartButton = view.findViewById(R.id.navigation_start_button);
        navigationStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                S2LS loc = Coordinator.get(getContext()).getS2LS(networkId);
                if (loc != null && route != null) {
                    loc.setCurrentTargetRoute(route, false);
                    switchToPage("nav_home");
                }
            }
        });
        useRealtimeCheckbox = view.findViewById(R.id.use_realtime_check);
        useRealtimeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                tryPlanRoute();
            }
        });
        routeEtaView = view.findViewById(R.id.route_eta_view);

        originPicker = view.findViewById(R.id.origin_picker);
        destinationPicker = view.findViewById(R.id.destination_picker);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        network = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
        loc = Coordinator.get(getContext()).getS2LS(networkId);
        // the network map might not be loaded yet
        if (network != null && loc != null) {
            populatePickers();
            updateClosedWarning();
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
        if (!loc.inNetwork()) {
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

        if (mListener != null) {
            String destId = mListener.getRouteDestination();
            if (destId != null) {
                destinationPicker.setSelectionById(destId);
            }
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
        if (realtimeRoute == null || neutralRoute == null) {
            // can't compute a route between the two stations
            return;
        }
        if (useRealtimeCheckbox.isChecked()) {
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
                FrameLayout lineStripeLayout = view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                FrameLayout topLineStripeLayout = view.findViewById(R.id.top_line_stripe_layout);
                Drawable background = Util.getColoredDrawableResource(getContext(), R.drawable.station_line_top, lineColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    topLineStripeLayout.setBackground(background);
                } else {
                    topLineStripeLayout.setBackgroundDrawable(background);
                }

                populateStationView(getActivity(), step.getStation(), view);

                if (step.getStation().getLines().size() > 1) {
                    Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(line.getId()));
                    drawable.setColorFilter(lineColor, PorterDuff.Mode.SRC_ATOP);

                    FrameLayout iconFrame = view.findViewById(R.id.frame_icon);
                    if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        iconFrame.setBackgroundDrawable(drawable);
                    } else {
                        iconFrame.setBackground(drawable);
                    }

                    TextView lineView = view.findViewById(R.id.line_name_view);
                    lineView.setText(String.format(getString(R.string.frag_route_line_name), Util.getLineNames(getContext(), line)[0]));
                    lineView.setTextColor(lineColor);

                    LinearLayout lineLayout = view.findViewById(R.id.line_layout);
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

                TextView directionView = view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                ((EnterStep) step).getDirection().getName())));

                if (line.getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                Map<String, LineStatusCache.Status> statuses = Coordinator.get(getContext()).getLineStatusCache().getLineStatus();
                if (statuses.get(line.getId()) != null &&
                        statuses.get(line.getId()).down) {
                    LinearLayout disturbancesWarningLayout = view.findViewById(R.id.disturbances_warning_layout);
                    disturbancesWarningLayout.setVisibility(View.VISIBLE);
                }
            } else if (step instanceof ChangeLineStep) {
                final ChangeLineStep lStep = (ChangeLineStep) step;
                view = getActivity().getLayoutInflater().inflate(R.layout.step_change_line, layoutRoute, false);

                int prevLineColor = lStep.getLine().getColor();
                FrameLayout prevLineStripeLayout = view.findViewById(R.id.prev_line_stripe_layout);
                prevLineStripeLayout.setBackgroundColor(prevLineColor);

                int nextLineColor = lStep.getTarget().getColor();
                FrameLayout nextLineStripeLayout = view.findViewById(R.id.next_line_stripe_layout);
                nextLineStripeLayout.setBackgroundColor(nextLineColor);

                FrameLayout middleLineStripeLayout = view.findViewById(R.id.middle_line_stripe_layout);
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{prevLineColor, nextLineColor});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    middleLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    middleLineStripeLayout.setBackground(gd);
                }

                populateStationView(getActivity(), lStep.getStation(), view);

                Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(lStep.getTarget().getId()));
                drawable.setColorFilter(nextLineColor, PorterDuff.Mode.SRC_ATOP);

                FrameLayout iconFrame = view.findViewById(R.id.frame_icon);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    iconFrame.setBackgroundDrawable(drawable);
                } else {
                    iconFrame.setBackground(drawable);
                }

                TextView lineView = view.findViewById(R.id.line_name_view);
                lineView.setText(String.format(getString(R.string.frag_route_line_name), Util.getLineNames(getContext(), lStep.getTarget())[0]));
                lineView.setTextColor(nextLineColor);

                LinearLayout lineLayout = view.findViewById(R.id.line_layout);
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

                TextView directionView = view.findViewById(R.id.direction_view);
                directionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_direction),
                                lStep.getDirection().getName())));

                if (lStep.getTarget().getUsualCarCount() < network.getUsualCarCount()) {
                    LinearLayout carsWarningLayout = view.findViewById(R.id.cars_warning_layout);
                    carsWarningLayout.setVisibility(View.VISIBLE);
                }

                Map<String, LineStatusCache.Status> statuses = Coordinator.get(getContext()).getLineStatusCache().getLineStatus();
                if (statuses.get(lStep.getTarget().getId()) != null &&
                        statuses.get(lStep.getTarget().getId()).down) {
                    LinearLayout disturbancesWarningLayout = view.findViewById(R.id.disturbances_warning_layout);
                    disturbancesWarningLayout.setVisibility(View.VISIBLE);
                }
            } else if (step instanceof ExitStep) {
                view = getActivity().getLayoutInflater().inflate(R.layout.step_exit_network, layoutRoute, false);

                int lineColor = step.getLine().getColor();
                FrameLayout lineStripeLayout = view.findViewById(R.id.line_stripe_layout);
                lineStripeLayout.setBackgroundColor(lineColor);

                FrameLayout bottomLineStripeLayout = view.findViewById(R.id.bottom_line_stripe_layout);
                Drawable background = Util.getColoredDrawableResource(getContext(), R.drawable.station_line_bottom, lineColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    bottomLineStripeLayout.setBackground(background);
                } else {
                    bottomLineStripeLayout.setBackgroundDrawable(background);
                }

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
        if (realtimeEqualsNeutral) {
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
        for (Connection c : route.getPath().getEdgeList()) {
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
        TextView stationView = view.findViewById(R.id.station_view);
        stationView.setText(station.getName());

        if (station.isExceptionallyClosed(station.getNetwork(), new Date())) {
            stationView.setTextColor(Color.GRAY);
        }

        ImageView crossView = view.findViewById(R.id.station_cross_image);
        if (crossView != null && station.isAlwaysClosed()) {
            crossView.setVisibility(View.VISIBLE);
        }

        if (showInfoIcons) {
            View separatorView = view.findViewById(R.id.feature_separator_view);
            LinearLayout iconsLayout = view.findViewById(R.id.icons_layout);

            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            Set<Integer> addedDrawables = new HashSet<>();
            for (String tag : station.getTags()) {
                int drawableResourceId = Util.getDrawableResourceIdForStationTag(tag);

                if (drawableResourceId != 0 && !addedDrawables.contains(drawableResourceId)) {
                    addedDrawables.add(drawableResourceId);
                    View iconView = inflater.inflate(R.layout.station_include_icon, iconsLayout, false);
                    ImageView iconImageView = iconView.findViewById(R.id.image_view);
                    iconImageView.setImageResource(drawableResourceId);
                    separatorView.setVisibility(View.VISIBLE);
                    iconsLayout.addView(iconView);
                }
            }
        }

        LinearLayout stationLayout = view.findViewById(R.id.station_layout);

        if (setOnClickListener) {
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
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    if (mListener != null) {
                        network = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
                        loc = Coordinator.get(getContext()).getS2LS(networkId);
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
