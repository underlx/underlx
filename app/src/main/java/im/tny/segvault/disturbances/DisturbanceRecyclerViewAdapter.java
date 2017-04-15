package im.tny.segvault.disturbances;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import im.tny.segvault.disturbances.DisturbanceFragment.OnListFragmentInteractionListener;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DisturbanceItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class DisturbanceRecyclerViewAdapter extends RecyclerView.Adapter<DisturbanceRecyclerViewAdapter.ViewHolder> {

    private final List<DisturbanceItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public DisturbanceRecyclerViewAdapter(List<DisturbanceItem> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_disturbance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mLineNameView.setText(String.format(holder.mView.getContext().getString(R.string.frag_disturbance_line), mValues.get(position).lineName));
        holder.mLineNameView.setTextColor(mValues.get(position).lineColor);
        holder.mDateView.setText(DateUtils.formatDateTime(holder.mView.getContext(), mValues.get(position).startTime.getTime(), DateUtils.FORMAT_SHOW_DATE));

        if(mValues.get(position).ended) {
            holder.mOngoingView.setVisibility(View.GONE);
        } else {
            holder.mOngoingView.setVisibility(View.VISIBLE);
        }

        holder.mLayout.removeAllViews();
        for(DisturbanceItem.Status s : mValues.get(position).statuses) {
            holder.mLayout.addView(new StatusView(holder.mView.getContext(), s));
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
        public final LinearLayout mLayout;
        public final TextView mLineNameView;
        public final TextView mDateView;
        public final TextView mOngoingView;
        public DisturbanceItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mLayout = (LinearLayout) view.findViewById(R.id.disturbance_status_layout);
            mLineNameView = (TextView) view.findViewById(R.id.line_name_view);
            mDateView = (TextView) view.findViewById(R.id.date_view);
            mOngoingView = (TextView) view.findViewById(R.id.ongoing_view);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.id + "'";
        }
    }

    public static class DisturbanceItem implements Serializable {
        public final String id;
        public final Date startTime;
        public final Date endTime;
        public final boolean ended;
        public final String lineId;
        public final String lineName;
        public final int lineColor;
        public final List<Status> statuses;

        public DisturbanceItem(API.Disturbance disturbance, Collection<Network> networks) {
            this.id = disturbance.id;
            this.startTime = new Date(disturbance.startTime[0] * 1000);
            this.endTime = new Date(disturbance.endTime[0] * 1000);
            this.ended = disturbance.ended;
            this.lineId = disturbance.line;
            String name = "Unknown line";
            int color = 0;
            for(Network n : networks) {
                if(n.getId().equals(disturbance.network)) {
                    for(Line l : n.getLines()) {
                        if(l.getId().equals(disturbance.line)) {
                            name = l.getName();
                            color = l.getColor();
                            break;
                        }
                    }
                }
            }
            this.lineName = name;
            this.lineColor = color;
            statuses = new ArrayList<>();
            for(API.Status s : disturbance.statuses) {
                statuses.add(new Status(new Date(s.time[0] * 1000), s.status, s.downtime));
            }
        }

        @Override
        public String toString() {
            return id;
        }

        public static class Status {
            public final Date date;
            public final String status;
            public final boolean isDowntime;

            public Status(Date date, String status, boolean isDowntime) {
                this.date = date;
                this.status = status;
                this.isDowntime = isDowntime;
            }
        }
    }


    private static class StatusView extends LinearLayout {
        private DisturbanceItem.Status status;
        public StatusView(Context context, DisturbanceItem.Status status) {
            super(context);
            this.setOrientation(HORIZONTAL);
            this.status = status;
            initializeViews(context);
        }

        public StatusView(Context context, AttributeSet attrs,
                          DisturbanceItem.Status status) {
            super(context, attrs);
            this.setOrientation(HORIZONTAL);
            this.status = status;
            initializeViews(context);
        }

        public StatusView(Context context,
                          AttributeSet attrs,
                          int defStyle,
                          DisturbanceItem.Status status) {
            super(context, attrs, defStyle);
            this.setOrientation(HORIZONTAL);
            this.status = status;
            initializeViews(context);
        }

        /**
         * Inflates the views in the layout.
         *
         * @param context the current context for the view.
         */
        private void initializeViews(Context context) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.fragment_disturbance_status_view, this);

            TextView timeView = (TextView) findViewById(R.id.time_view);
            TextView statusView = (TextView) findViewById(R.id.status_view);
            ImageView iconView = (ImageView) findViewById(R.id.icon_view);

            timeView.setText(DateUtils.formatDateTime(context, status.date.getTime(), DateUtils.FORMAT_SHOW_TIME));
            statusView.setText(status.status);

            if(status.isDowntime) {
                iconView.setVisibility(GONE);
            } else {
                iconView.setVisibility(VISIBLE);
            }
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
        }
    }
}

