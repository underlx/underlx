package im.tny.segvault.disturbances.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Date;
import java.util.Formatter;
import java.util.List;

import im.tny.segvault.disturbances.ui.fragment.HomeLinesFragment.OnListFragmentInteractionListener;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.subway.Line;

/**
 * {@link RecyclerView.Adapter} that can display a {@link LineItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class LineRecyclerViewAdapter extends RecyclerView.Adapter<LineRecyclerViewAdapter.ViewHolder> {

    private final List<LineItem> mValues;
    private final OnListFragmentInteractionListener mListener;
    private Context context;

    public LineRecyclerViewAdapter(List<LineItem> values, OnListFragmentInteractionListener listener) {
        mValues = values;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_line, parent, false);
        context = parent.getContext();
        return new ViewHolder(view);
    }

    private int darkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.6f; // value component
        return Color.HSVToColor(hsv);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mNameView.setText(holder.mItem.name);
        if(holder.mItem.isInterrupted) {
            long downMinutes = (((new Date()).getTime() / 60000) - (holder.mItem.downSince.getTime() / 60000));
            holder.mStatusDescView.setVisibility(View.VISIBLE);
            holder.mStatusDescView.setText(String.format(holder.mView.getContext().getString(R.string.frag_lines_duration), downMinutes));
            holder.mStatusView.setImageResource(R.drawable.ic_alert_octagon_white_24dp);
            setDownGradient(holder);
        } else if(holder.mItem.down) {
            long downMinutes = (((new Date()).getTime() / 60000) - (holder.mItem.downSince.getTime() / 60000));
            holder.mStatusDescView.setVisibility(View.VISIBLE);
            holder.mStatusDescView.setText(String.format(holder.mView.getContext().getString(R.string.frag_lines_duration), downMinutes));
            holder.mStatusView.setImageResource(R.drawable.ic_warning_white);
            setDownGradient(holder);
        } else if (holder.mItem.exceptionallyClosed) {
            setDownGradient(holder);
            Formatter f = new Formatter();
            DateUtils.formatDateRange(context, f, holder.mItem.closedUntil, holder.mItem.closedUntil, DateUtils.FORMAT_SHOW_TIME, Time.TIMEZONE_UTC);
            holder.mStatusDescView.setText(String.format(holder.mView.getContext().getString(R.string.frag_lines_until), f.toString()));
            holder.mStatusView.setImageResource(R.drawable.ic_close_white_24dp);
        } else if (holder.mItem.scheduleClosed) {
            holder.mView.setBackgroundColor(holder.mItem.color);
            holder.mStatusDescView.setVisibility(View.GONE);
            holder.mStatusView.setImageResource(R.drawable.ic_sleep_white_24dp);
        } else {
            holder.mView.setBackgroundColor(holder.mItem.color);
            holder.mStatusDescView.setVisibility(View.GONE);
            holder.mStatusView.setImageResource(R.drawable.ic_done_white);
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

    private void setDownGradient(final ViewHolder holder) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{holder.mItem.color, darkerColor(holder.mItem.color)});
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            holder.mView.setBackgroundDrawable(gd);
        } else {
            holder.mView.setBackground(gd);
        }
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mNameView;
        public final TextView mStatusDescView;
        public final ImageView mStatusView;
        public LineItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mNameView = (TextView) view.findViewById(R.id.line_name_view);
            mStatusDescView = (TextView) view.findViewById(R.id.line_status_desc_view);
            mStatusView = (ImageView) view.findViewById(R.id.line_status_view);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mNameView.getText() + "'";
        }
    }

    public static class LineItem implements Serializable {
        public final String id;
        public final String name;
        public final int color;
        public final boolean down;
        public final Date downSince;
        public final boolean exceptionallyClosed;
        public final boolean scheduleClosed;
        public final boolean isInterrupted;
        public final long closedUntil;

        public LineItem(Line line) {
            this.id = line.getId();
            this.name = line.getName();
            this.color = line.getColor();
            this.down = false;
            this.downSince = new Date();
            this.exceptionallyClosed = line.isExceptionallyClosed(new Date());
            this.scheduleClosed = !line.isOpen();
            this.isInterrupted = false;
            this.closedUntil = line.getNextOpenTime(new Date());
        }

        public LineItem(Line line, Date downSince) {
            this.id = line.getId();
            this.name = line.getName();
            this.color = line.getColor();
            this.down = true;
            this.downSince = downSince;
            this.exceptionallyClosed = line.isExceptionallyClosed(new Date());
            this.scheduleClosed = !line.isOpen();
            this.isInterrupted = false;
            this.closedUntil = line.getNextOpenTime(new Date());
        }

        public LineItem(Line line, Date downSince, boolean isInterrupted) {
            this.id = line.getId();
            this.name = line.getName();
            this.color = line.getColor();
            this.down = true;
            this.downSince = downSince;
            this.exceptionallyClosed = line.isExceptionallyClosed(new Date());
            this.scheduleClosed = !line.isOpen();
            this.isInterrupted = isInterrupted;
            this.closedUntil = line.getNextOpenTime(new Date());
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
