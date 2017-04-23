package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.List;

import im.tny.segvault.subway.Connection;
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
public class RouteFragment extends Fragment {
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
        if (mListener != null) {
            mListener.setActionBarTitle(getString(R.string.frag_route_title));
            mListener.checkNavigationDrawerItem(R.id.nav_plan_route);
        }
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();
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

        if (mListener != null) {
            network = mListener.getLocationService().getNetwork("pt-ml");
            if (network == null) {
                // TODO deal with the fact that the network map might not be loaded yet
            }
            List<Station> stations = new ArrayList<>(network.vertexSet());

            LinearLayout pickersLayout = (LinearLayout) view.findViewById(R.id.layout_pickers);

            originPicker = (StationPickerView) view.findViewById(R.id.origin_picker);
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

            destinationPicker = (StationPickerView) view.findViewById(R.id.destination_picker);
            destinationPicker.setStations(stations);
            destinationPicker.setOnStationSelectedListener(new StationPickerView.OnStationSelectedListener() {
                @Override
                public void onStationSelected(Station station) {
                    tryPlanRoute();
                    destinationPicker.clearFocus();
                    // Check if no view has focus:
                    View view = getActivity().getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
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

        return view;
    }

    private void tryPlanRoute() {
        if (originPicker.getSelection() == null || destinationPicker.getSelection() == null) {
            // not enough information
            return;
        }

        AStarShortestPath as = new AStarShortestPath(network);
        GraphPath gp = as.getShortestPath(originPicker.getSelection(), destinationPicker.getSelection(), new AStarAdmissibleHeuristic<Station>() {
            @Override
            public double getCostEstimate(Station sourceVertex, Station targetVertex) {
                return 0;
            }
        });
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

                TextView descriptionView = (TextView) view.findViewById(R.id.description_view);
                descriptionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_step_enter_network),
                                c.getSource().getName(),
                                c.getTarget().getLines().get(0).getDirectionForConnection(c).getName(),
                                network.outDegreeOf(c.getSource()) > 2 ?
                                        String.format(getString(R.string.frag_route_step_enter_network_line),
                                                c.getSource().getLines().get(0).getColor() & 0x00FFFFFF,
                                                c.getSource().getLines().get(0).getName())
                                        : "")));

                if (c.getSource().getLines().get(0).getCarCount() < network.getUsualCarCount()) {
                    TextView carsWarningView = (TextView) view.findViewById(R.id.cars_warning_view);
                    carsWarningView.setVisibility(View.VISIBLE);
                }

                layoutRoute.addView(view);
                isFirst = false;
            }

            if (i == el.size() - 1) {
                View view = getActivity().getLayoutInflater().inflate(R.layout.step_exit_network, layoutRoute, false);

                TextView descriptionView = (TextView) view.findViewById(R.id.description_view);
                descriptionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_step_exit_network),
                                c.getTarget().getName())));
                layoutRoute.addView(view);
            } else if (c instanceof Transfer) {
                Connection c2 = el.get(i + 1);

                View view = getActivity().getLayoutInflater().inflate(R.layout.step_change_line, layoutRoute, false);

                TextView descriptionView = (TextView) view.findViewById(R.id.description_view);
                descriptionView.setText(Util.fromHtml(
                        String.format(getString(R.string.frag_route_step_change_line),
                                c.getSource().getName(),
                                c.getTarget().getLines().get(0).getColor() & 0x00FFFFFF,
                                c.getTarget().getLines().get(0).getName(),
                                c2.getTarget().getLines().get(0).getDirectionForConnection(c2).getName())));

                if (c.getTarget().getLines().get(0).getCarCount() < network.getUsualCarCount()) {
                    TextView carsWarningView = (TextView) view.findViewById(R.id.cars_warning_view);
                    carsWarningView.setVisibility(View.VISIBLE);
                }

                layoutRoute.addView(view);
            }
        }
        if (layoutRoute.getChildCount() == 0) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.step_already_there, layoutRoute, false);

            TextView descriptionView = (TextView) view.findViewById(R.id.description_view);
            descriptionView.setText(Util.fromHtml(
                    String.format(getString(R.string.frag_route_step_already_there),
                            originPicker.getSelection().getName())));

            if (originPicker.getSelection().getLines().get(0).getCarCount() < network.getUsualCarCount() ||
                    destinationPicker.getSelection().getLines().get(0).getCarCount() < network.getUsualCarCount()) {
                TextView carsWarningView = (TextView) view.findViewById(R.id.cars_warning_view);
                carsWarningView.setVisibility(View.VISIBLE);
            }

            layoutRoute.addView(view);
        }

        layoutInstructions.setVisibility(View.GONE);
        layoutRoute.setVisibility(View.VISIBLE);
        swapButton.setVisibility(View.VISIBLE);
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
    public interface OnFragmentInteractionListener extends OnTopFragmentInteractionListener {
        MainService getLocationService();
    }
}
