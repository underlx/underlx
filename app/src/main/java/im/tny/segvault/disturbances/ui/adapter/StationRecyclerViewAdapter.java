package im.tny.segvault.disturbances.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.fragment.HomeFavoriteStationsFragment;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.subway.Station;

/**
 * {@link RecyclerView.Adapter} that can display a {@link StationItem} and makes a call to the
 * specified {@link HomeFavoriteStationsFragment.OnListFragmentInteractionListener}.
 */
public class StationRecyclerViewAdapter extends RecyclerView.Adapter<StationRecyclerViewAdapter.ViewHolder> {

    private final List<StationItem> mValues;
    private final HomeFavoriteStationsFragment.OnListFragmentInteractionListener mListener;
    private Context context;
    private MapManager mapManager;

    public StationRecyclerViewAdapter(List<StationItem> values, HomeFavoriteStationsFragment.OnListFragmentInteractionListener listener) {
        mValues = values;
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_row_station, parent, false);
        context = parent.getContext();
        mapManager = Coordinator.get(context).getMapManager();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);

        // remove background from path_station since adapter_row_station already sets selectableItemBackground
        holder.mView.findViewById(R.id.path_station_root_layout).setBackgroundColor(Color.TRANSPARENT);

        final Station station = mapManager.getNetwork(holder.mItem.networkId).getStation(holder.mItem.id);

        TextView timeView = holder.mView.findViewById(R.id.time_view);
        timeView.setVisibility(View.INVISIBLE);
        ViewGroup.LayoutParams layoutParams = timeView.getLayoutParams();
        final float scale = timeView.getContext().getResources().getDisplayMetrics().density;
        layoutParams.width = (int) (30 * scale);
        timeView.setLayoutParams(layoutParams);

        FrameLayout prevLineStripeLayout = holder.mView.findViewById(R.id.prev_line_stripe_layout);
        prevLineStripeLayout.setVisibility(View.GONE);
        FrameLayout nextLineStripeLayout = holder.mView.findViewById(R.id.next_line_stripe_layout);
        nextLineStripeLayout.setVisibility(View.GONE);

        FrameLayout leftLineStripeLayout = holder.mView.findViewById(R.id.left_line_stripe_layout);
        leftLineStripeLayout.setVisibility(View.VISIBLE);
        FrameLayout backLineStripeLayout = holder.mView.findViewById(R.id.back_line_stripe_layout);
        backLineStripeLayout.setVisibility(View.VISIBLE);
        FrameLayout rightLineStripeLayout = holder.mView.findViewById(R.id.right_line_stripe_layout);
        rightLineStripeLayout.setVisibility(View.VISIBLE);

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0, station.getLines().get(0).getColor()});
        gd.setCornerRadius(0f);

        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            rightLineStripeLayout.setBackgroundDrawable(gd);
        } else {
            rightLineStripeLayout.setBackground(gd);
        }

        gd = new GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{station.getLines().get(0).getColor(), station.getLines().get(station.getLines().size() > 1 ? 1 : 0).getColor()});
        gd.setCornerRadius(0f);

        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            backLineStripeLayout.setBackgroundDrawable(gd);
        } else {
            backLineStripeLayout.setBackground(gd);
        }

        gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0, station.getLines().get(station.getLines().size() > 1 ? 1 : 0).getColor()});
        gd.setCornerRadius(0f);

        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            leftLineStripeLayout.setBackgroundDrawable(gd);
        } else {
            leftLineStripeLayout.setBackground(gd);
        }

        RouteFragment.populateStationView(context, station, holder.mView, true, false);

        TableLayout etasLayout = holder.mView.findViewById(R.id.etas_layout);

        Map<String, API.MQTTvehicleETA> etas = Coordinator.get(context).getMqttManager().getVehicleETAsForStation(station);
        if (etas.size() > 0 && !station.isAlwaysClosed()) {
            List<MainService.ETArow> rows = MainService.getETArows(context, etas, station, null, false, true);
            for (int i = 0; i < rows.size(); i += 2) {
                boolean isSingle = i == rows.size() - 1;
                TableRow row;
                if (isSingle) {
                    row = (TableRow) LayoutInflater.from(context).inflate(R.layout.adapter_row_station_row_single, etasLayout, false);
                } else {
                    row = (TableRow) LayoutInflater.from(context).inflate(R.layout.adapter_row_station_row_double, etasLayout, false);
                }
                TextView destinationView = row.findViewById(R.id.destination1_view);
                destinationView.setText(rows.get(i).direction.getName(isSingle ? 25 : 9));
                destinationView.setTextColor(rows.get(i).directionStop.getLine().getColor());
                TextView etaView = row.findViewById(R.id.eta1_view);
                etaView.setText(rows.get(i).eta);
                if (!isSingle) {
                    destinationView = row.findViewById(R.id.destination2_view);
                    destinationView.setText(rows.get(i + 1).direction.getName(9));
                    destinationView.setTextColor(rows.get(i + 1).directionStop.getLine().getColor());
                    etaView = row.findViewById(R.id.eta2_view);
                    etaView.setText(rows.get(i + 1).eta);
                }
                etasLayout.addView(row);
            }
        }

        holder.mView.setOnClickListener(v -> {
            if (null != mListener) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                mListener.onListFragmentStationSelected(station);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public StationItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
        }
    }

    public static class StationItem implements Serializable {
        public final String id;
        public final String networkId;

        public StationItem(Station station, Context context) {
            this.id = station.getId();
            this.networkId = station.getNetwork().getId();
        }

        public StationItem(String stationId, String networkId, Context context) {
            this.id = stationId;
            this.networkId = networkId;
        }
    }
}
