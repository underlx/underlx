package im.tny.segvault.disturbances;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import im.tny.segvault.disturbances.LineFragment.OnListFragmentInteractionListener;
import im.tny.segvault.subway.Line;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link LineItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class MyLineRecyclerViewAdapter extends RecyclerView.Adapter<MyLineRecyclerViewAdapter.ViewHolder> {

    private final List<LineItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public MyLineRecyclerViewAdapter(OnListFragmentInteractionListener listener, List<LineItem> values) {
        mValues = values;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mView.setBackgroundColor(holder.mItem.color);
        holder.mNameView.setText(holder.mItem.name);
        if(holder.mItem.down) {
            long downMinutes = (((new Date()).getTime()/60000) - (holder.mItem.downSince.getTime()/60000));
            holder.mStatusDescView.setVisibility(View.VISIBLE);
            holder.mStatusDescView.setText(String.format(holder.mView.getContext().getString(R.string.frag_lines_duration), downMinutes));
            holder.mStatusView.setImageResource(R.drawable.ic_warning_white);
        } else {
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

        public LineItem(Line line) {
            this.id = line.getId();
            this.name = line.getName();
            this.color = line.getColor();
            this.down = false;
            this.downSince = new Date();
        }

        public LineItem(Line line, Date downSince) {
            this.id = line.getId();
            this.name = line.getName();
            this.color = line.getColor();
            this.down = true;
            this.downSince = downSince;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
