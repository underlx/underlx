package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import io.realm.Realm;
import io.realm.RealmQuery;

/**
 * Created by gabriel on 5/10/17.
 */

public class StationFragment extends BottomSheetDialogFragment {
    private StationFragment.OnFragmentInteractionListener mListener;

    private static final String ARG_STATION_ID = "stationId";
    private static final String ARG_NETWORK_ID = "networkId";

    private String stationId;
    private String networkId;

    private TextView stationNameView;
    private LinearLayout lineIconsLayout;

    public StationFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static StationFragment newInstance(String networkId, String stationId) {
        StationFragment fragment = new StationFragment();
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
        View view = inflater.inflate(R.layout.fragment_station, container, false);

        stationNameView = (TextView) view.findViewById(R.id.station_name_view);
        lineIconsLayout = (LinearLayout) view.findViewById(R.id.line_icons_layout);

        View.OnClickListener clickDetails = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, stationId);
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, networkId);
                startActivity(intent);
                dismiss();
            }
        };
        Button moreInfoButton = (Button) view.findViewById(R.id.more_info_button);
        moreInfoButton.setOnClickListener(clickDetails);
        stationNameView.setOnClickListener(clickDetails);

        if (mListener == null)
            return view;
        MainService service = mListener.getMainService();
        if (service != null) {
            Network net = service.getNetwork(networkId);
            Station station = net.getStation(stationId).get(0);

            stationNameView.setText(station.getName());

            Set<Line> lineset = new HashSet<>(station.getLines());

            for (Connection c : station.getTransferEdges(net)) {
                lineset.addAll(c.getTarget().getLines());
            }
            List<Line> lines = new ArrayList<>(lineset);
            Collections.sort(lines, Collections.reverseOrder(new Comparator<Line>() {
                @Override
                public int compare(Line l1, Line l2) {
                    return l1.getName().compareTo(l2.getName());
                }
            }));

            for (Line l : lines) {
                Drawable drawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLineId(l.getId()));
                drawable.setColorFilter(l.getColor(), PorterDuff.Mode.SRC_ATOP);

                int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics());
                FrameLayout iconFrame = new FrameLayout(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
                int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    params.setMarginEnd(margin);
                }
                params.setMargins(0, 0, margin, 0);
                iconFrame.setLayoutParams(params);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    iconFrame.setBackgroundDrawable(drawable);
                } else {
                    iconFrame.setBackground(drawable);
                }
                lineIconsLayout.addView(iconFrame);
            }

            // Connections
            TextView connectionsTitleView = (TextView) view.findViewById(R.id.connections_title_view);
            LinearLayout busLayout = (LinearLayout) view.findViewById(R.id.feature_bus_layout);
            if (station.getFeatures().bus) {
                busLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout boatLayout = (LinearLayout) view.findViewById(R.id.feature_boat_layout);
            if (station.getFeatures().boat) {
                boatLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout trainLayout = (LinearLayout) view.findViewById(R.id.feature_train_layout);
            if (station.getFeatures().train) {
                trainLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            LinearLayout airportLayout = (LinearLayout) view.findViewById(R.id.feature_airport_layout);
            if (station.getFeatures().airport) {
                airportLayout.setVisibility(View.VISIBLE);
                connectionsTitleView.setVisibility(View.VISIBLE);
            }

            // Accessibility
            TextView accessibilityTitleView = (TextView) view.findViewById(R.id.accessibility_title_view);
            LinearLayout liftLayout = (LinearLayout) view.findViewById(R.id.feature_lift_layout);
            if (station.getFeatures().lift) {
                liftLayout.setVisibility(View.VISIBLE);
                accessibilityTitleView.setVisibility(View.VISIBLE);
            }
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof StationFragment.OnFragmentInteractionListener) {
            mListener = (StationFragment.OnFragmentInteractionListener) context;
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

