package im.tny.segvault.disturbances.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sufficientlysecure.htmltextview.HtmlHttpImageGetter;
import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.top.DisturbanceFragment.OnListFragmentInteractionListener;
import im.tny.segvault.disturbances.ui.activity.LineActivity;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DisturbanceItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class DisturbanceRecyclerViewAdapter extends RecyclerView.Adapter<DisturbanceRecyclerViewAdapter.ViewHolder> {

    private final List<DisturbanceItem> mValues;
    private final OnListFragmentInteractionListener mListener;
    private Context context;

    public DisturbanceRecyclerViewAdapter(List<DisturbanceItem> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_disturbance, parent, false);
        context = parent.getContext();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mLineNameView.setText(String.format(holder.mView.getContext().getString(R.string.frag_disturbance_line), holder.mItem.lineName));
        holder.mLineNameView.setTextColor(holder.mItem.lineColor);
        holder.mDateView.setText(DateUtils.formatDateTime(holder.mView.getContext(), holder.mItem.startTime.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR));

        Drawable drawable = ContextCompat.getDrawable(holder.mView.getContext(), Util.getDrawableResourceIdForLineId(holder.mItem.lineId));
        drawable.setColorFilter(holder.mItem.lineColor, PorterDuff.Mode.SRC_ATOP);

        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            holder.iconLayout.setBackgroundDrawable(drawable);
        } else {
            holder.iconLayout.setBackground(drawable);
        }

        if(holder.mItem.ended) {
            holder.mOngoingView.setVisibility(View.GONE);
        } else {
            holder.mOngoingView.setVisibility(View.VISIBLE);
        }

        if(!holder.mItem.notes.isEmpty()) {
            holder.notesView.setHtml(holder.mItem.notes, new HtmlHttpImageGetter(holder.notesView, null, true));
            holder.notesLayout.setVisibility(View.VISIBLE);
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

        View.OnClickListener lineClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, LineActivity.class);
                intent.putExtra(LineActivity.EXTRA_LINE_ID, holder.mItem.lineId);
                intent.putExtra(LineActivity.EXTRA_NETWORK_ID, holder.mItem.networkId);
                context.startActivity(intent);
            }
        };

        holder.mLineNameView.setOnClickListener(lineClickListener);
        holder.iconLayout.setOnClickListener(lineClickListener);
        holder.shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT, String.format(context.getString(R.string.link_format_disturbance), holder.mItem.id));
                context.startActivity(Intent.createChooser(sendIntent, null));
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
        public final FrameLayout iconLayout;
        public final LinearLayout notesLayout;
        public final HtmlTextView notesView;
        public final ImageButton shareButton;
        public DisturbanceItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mLayout = (LinearLayout) view.findViewById(R.id.disturbance_status_layout);
            mLineNameView = (TextView) view.findViewById(R.id.line_name_view);
            mDateView = (TextView) view.findViewById(R.id.date_view);
            mOngoingView = (TextView) view.findViewById(R.id.ongoing_view);
            iconLayout = (FrameLayout) view.findViewById(R.id.frame_icon);
            notesLayout = (LinearLayout) view.findViewById(R.id.disturbance_notes_layout);
            notesView = (HtmlTextView) view.findViewById(R.id.disturbance_notes_view);
            shareButton = (ImageButton) view.findViewById(R.id.share_button);
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
        public final String networkId;
        public final String lineId;
        public final String lineName;
        public final int lineColor;
        public final List<Status> statuses;
        public final String notes;

        public DisturbanceItem(API.Disturbance disturbance, Collection<Network> networks, Context context) {
            this.id = disturbance.id;
            this.startTime = new Date(disturbance.startTime[0] * 1000);
            this.endTime = new Date(disturbance.endTime[0] * 1000);
            this.ended = disturbance.ended;
            this.lineId = disturbance.line;
            this.notes = disturbance.notes;
            String name = "Unknown line";
            String netId = "";
            int color = 0;
            for(Network n : networks) {
                if(n.getId().equals(disturbance.network)) {
                    netId = n.getId();
                    for(Line l : n.getLines()) {
                        if(l.getId().equals(disturbance.line)) {
                            name = Util.getLineNames(context, l)[0];
                            color = l.getColor();
                            break;
                        }
                    }
                }
            }
            this.lineName = name;
            this.lineColor = color;
            this.networkId = netId;
            statuses = new ArrayList<>();
            for(API.Status s : disturbance.statuses) {
                statuses.add(new Status(new Date(s.time[0] * 1000), s.status, s.downtime));
            }
            Collections.sort(statuses, new Comparator<Status>() {
                @Override
                public int compare(Status o1, Status o2) {
                    return o1.date.compareTo(o2.date);
                }
            });
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

