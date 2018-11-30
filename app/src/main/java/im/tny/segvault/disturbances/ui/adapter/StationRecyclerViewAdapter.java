package im.tny.segvault.disturbances.ui.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.List;

import im.tny.segvault.disturbances.Coordinator;
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

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.path_station, parent, false);
        context = parent.getContext();
        mapManager = Coordinator.get(context).getMapManager();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);

        final Station station = mapManager.getNetwork(holder.mItem.networkId).getStation(holder.mItem.id);

        TextView timeView = holder.mView.findViewById(R.id.time_view);
        timeView.setVisibility(View.INVISIBLE);

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
