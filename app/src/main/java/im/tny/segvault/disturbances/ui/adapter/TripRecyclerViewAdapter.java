package im.tny.segvault.disturbances.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.s2ls.Path;
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
    private final boolean homeScreenList;

    public TripRecyclerViewAdapter(List<TripItem> items, OnListFragmentInteractionListener listener, boolean forHomeScreen) {
        mValues = items;
        mListener = listener;
        homeScreenList = forHomeScreen;
    }

    public TripRecyclerViewAdapter(List<TripItem> items, OnListFragmentInteractionListener listener) {
        this(items, listener, false);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int l = R.layout.fragment_trip_history;
        if (homeScreenList) {
            l = R.layout.fragment_unconfirmed_trip;
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(l, parent, false);
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

        if (holder.mItem.isVisit) {
            holder.destinationLayout.setVisibility(View.GONE);
            holder.secondDotView.setVisibility(View.GONE);

            Drawable background = Util.getColoredDrawableResource(holder.originView.getContext(), R.drawable.station_line_single, Color.GRAY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                holder.lineLayout.setBackground(background);
            } else {
                holder.lineLayout.setBackgroundDrawable(background);
            }
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

            Drawable background = Util.getColoredDrawableResource(holder.originView.getContext(), R.drawable.station_line_top, colors[0]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                holder.topLineLayout.setBackground(background);
            } else {
                holder.topLineLayout.setBackgroundDrawable(background);
            }

            background = Util.getColoredDrawableResource(holder.originView.getContext(), R.drawable.station_line_bottom, colors[colors.length-1]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                holder.bottomLineLayout.setBackground(background);
            } else {
                holder.bottomLineLayout.setBackgroundDrawable(background);
            }
        }

        holder.mView.setOnClickListener(v -> {
            if (null != mListener) {
                mListener.onListFragmentClick(holder.mItem);
            }
        });
        holder.mView.setLongClickable(true);

        if (homeScreenList) {
            holder.confirmButton.setOnClickListener(v -> {
                if (null != mListener) {
                    mListener.onListFragmentConfirmButtonClick(holder.mItem);
                }
            });

            holder.correctButton.setOnClickListener(v -> {
                if (null != mListener) {
                    mListener.onListFragmentCorrectButtonClick(holder.mItem);
                }
            });
        }
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
        public final FrameLayout topLineLayout;
        public final FrameLayout bottomLineLayout;
        public final FrameLayout lineLayout;
        public final LinearLayout destinationLayout;
        public final ImageView secondDotView;

        public final Button confirmButton;
        public final Button correctButton;
        public TripItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            originView = view.findViewById(R.id.origin_view);
            originTimeView = view.findViewById(R.id.origin_time_view);
            destinationView = view.findViewById(R.id.destination_view);
            destinationTimeView = view.findViewById(R.id.destination_time_view);
            topLineLayout = view.findViewById(R.id.top_line_stripe_layout);
            bottomLineLayout = view.findViewById(R.id.bottom_line_stripe_layout);
            lineLayout = view.findViewById(R.id.line_stripe_layout);
            destinationLayout = view.findViewById(R.id.destination_layout);
            secondDotView = view.findViewById(R.id.second_dot_view);
            if (homeScreenList) {
                confirmButton = view.findViewById(R.id.confirm_button);
                correctButton = view.findViewById(R.id.correct_button);
            } else {
                confirmButton = null;
                correctButton = null;
            }
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.id + "'";
        }
    }

    public static class TripItem implements Serializable {
        public final String id;
        public final String networkId;
        public final String originId;
        public final String originName;
        public final Date originTime;
        public final int originColor;
        public final String destName;
        public final Date destTime;
        public final int destColor;
        public final List<Integer> lineColors;
        public final boolean isVisit;
        public final int length;
        public final int timeableLength;
        public final long movementMilliseconds;

        public TripItem(Trip trip, Collection<Network> networks) throws InvalidObjectException {
            this.id = trip.getId();

            Network network = null;
            for (Network n : networks) {
                if (n.getId().equals(trip.getPath().get(0).getStation().getNetwork())) {
                    network = n;
                    break;
                }
            }
            if (network == null) {
                // we probably lost the network map
                this.networkId = "";
                this.originId = "";
                this.originName = "";
                this.originTime = new Date();
                this.originColor = Color.BLACK;

                this.destName = "";
                this.destTime = new Date();
                this.destColor = Color.BLACK;

                this.lineColors = new ArrayList<>();
                this.isVisit = true;
                this.length = 0;
                this.timeableLength = 0;
                this.movementMilliseconds = 0;
                return;
            }
            this.networkId = network.getId();
            this.originId = network.getStation(trip.getPath().get(0).getStation().getId()).getId();
            this.originName = network.getStation(trip.getPath().get(0).getStation().getId()).getName();
            this.originTime = trip.getPath().get(0).getEntryDate();
            this.originColor = Color.BLACK;

            this.destName = network.getStation(trip.getPath().get(trip.getPath().size() - 1).getStation().getId()).getName();
            this.destTime = trip.getPath().get(trip.getPath().size() - 1).getLeaveDate();
            this.destColor = Color.BLACK;

            this.lineColors = new ArrayList<>();
            Path path = trip.toConnectionPath(network);
            if(path == null) {
                throw new InvalidObjectException("Corrupt trip in DB");
            }
            List<Connection> edges = path.getEdgeList();
            if (edges.size() > 0) {
                lineColors.add(edges.get(0).getSource().getLine().getColor());
                for (Connection c : edges) {
                    if (c instanceof Transfer) {
                        lineColors.add(c.getSource().getLine().getColor());
                        lineColors.add(c.getTarget().getLine().getColor());
                    }
                }
                lineColors.add(edges.get(edges.size() - 1).getTarget().getLine().getColor());
                isVisit = false;
            } else {
                isVisit = true;
            }
            this.length = path.getPhysicalLength();
            this.timeableLength = path.getTimeablePhysicalLength();
            this.movementMilliseconds = path.getMovementMilliseconds();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public interface OnListFragmentInteractionListener {
        void onListFragmentClick(TripRecyclerViewAdapter.TripItem item);

        void onListFragmentConfirmButtonClick(TripRecyclerViewAdapter.TripItem item);

        void onListFragmentCorrectButtonClick(TripRecyclerViewAdapter.TripItem item);
    }
}

