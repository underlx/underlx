package im.tny.segvault.disturbances;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.TripHistoryFragment.OnListFragmentInteractionListener;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Transfer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link TripItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class TripRecyclerViewAdapter extends RecyclerView.Adapter<TripRecyclerViewAdapter.ViewHolder> {

    private final List<TripItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public TripRecyclerViewAdapter(List<TripItem> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_trip_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.originView.setText(holder.mItem.originName);
        holder.originView.setTextColor(holder.mItem.originColor);
        if (DateUtils.isToday(holder.mItem.originTime.getTime())) {
            holder.originTimeView.setText(
                    DateUtils.formatDateTime(
                            holder.mView.getContext(),
                            holder.mItem.originTime.getTime(),
                            DateUtils.FORMAT_SHOW_TIME));
        } else {
            holder.originTimeView.setText(
                    DateUtils.formatDateTime(
                            holder.mView.getContext(),
                            holder.mItem.originTime.getTime(),
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NUMERIC_DATE));
        }

        if (holder.mItem.isTrip) {
            holder.destinationLayout.setVisibility(View.GONE);
            holder.lineLayout.setVisibility(View.GONE);
            holder.secondDotView.setVisibility(View.GONE);
        } else {
            holder.destinationView.setText(holder.mItem.destName);
            holder.destinationView.setTextColor(holder.mItem.destColor);
            holder.destinationTimeView.setText(
                    DateUtils.formatDateTime(
                            holder.mView.getContext(), holder.mItem.destTime.getTime(), DateUtils.FORMAT_SHOW_TIME));

            int[] colors = new int[holder.mItem.lineColors.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = holder.mItem.lineColors.get(i);
            }
            if (colors.length == 0) {
                colors = new int[2];
                colors[0] = Color.GRAY;
                colors[1] = Color.GRAY;
            } else if (colors.length == 1) {
                int[] c = new int[2];
                c[0] = colors[0];
                c[1] = colors[0];
                colors = c;
            }
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
            gd.setCornerRadius(0f);

            if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                holder.lineLayout.setBackgroundDrawable(gd);
            } else {
                holder.lineLayout.setBackground(gd);
            }
        }

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListFragmentInteraction(holder.mItem);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView originView;
        public final TextView originTimeView;
        public final TextView destinationView;
        public final TextView destinationTimeView;
        public final FrameLayout lineLayout;
        public final LinearLayout destinationLayout;
        public final ImageView secondDotView;
        public TripItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            originView = (TextView) view.findViewById(R.id.origin_view);
            originTimeView = (TextView) view.findViewById(R.id.origin_time_view);
            destinationView = (TextView) view.findViewById(R.id.destination_view);
            destinationTimeView = (TextView) view.findViewById(R.id.destination_time_view);
            lineLayout = (FrameLayout) view.findViewById(R.id.line_stripe_layout);
            destinationLayout = (LinearLayout) view.findViewById(R.id.destination_layout);
            secondDotView = (ImageView) view.findViewById(R.id.second_dot_view);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.id + "'";
        }
    }

    public static class TripItem implements Serializable {
        public final String id;
        public final String originName;
        public final Date originTime;
        public final int originColor;
        public final String destName;
        public final Date destTime;
        public final int destColor;
        public final List<Integer> lineColors;
        public final boolean isTrip;

        public TripItem(Trip trip, Collection<Network> networks) {
            this.id = trip.getId();

            Network network = null;
            for (Network n : networks) {
                if (n.getId().equals(trip.getPath().get(0).getStation().getNetwork())) {
                    network = n;
                    break;
                }
            }
            this.originName = network.getStation(trip.getPath().get(0).getStation().getId()).get(0).getName();
            this.originTime = trip.getPath().get(0).getEntryDate();
            this.originColor = Color.BLACK;

            this.destName = network.getStation(trip.getPath().get(trip.getPath().size() - 1).getStation().getId()).get(0).getName();
            this.destTime = trip.getPath().get(trip.getPath().size() - 1).getLeaveDate();
            this.destColor = Color.BLACK;

            this.lineColors = new ArrayList<>();
            List<Connection> path = trip.toConnectionPath(network);
            if (path.size() > 0) {
                lineColors.add(path.get(0).getSource().getLines().get(0).getColor());
                for (Connection c : path) {
                    if (c instanceof Transfer) {
                        lineColors.add(c.getSource().getLines().get(0).getColor());
                        lineColors.add(c.getTarget().getLines().get(0).getColor());
                    }
                }
                lineColors.add(path.get(path.size() - 1).getTarget().getLines().get(0).getColor());
                isTrip = false;
            } else {
                isTrip = true;
            }
        }

        @Override
        public String toString() {
            return id;
        }
    }
}

