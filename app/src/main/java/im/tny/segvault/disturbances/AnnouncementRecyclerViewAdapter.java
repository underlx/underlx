package im.tny.segvault.disturbances;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
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

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * {@link RecyclerView.Adapter} that can display a {@link AnnouncementItem} and makes a call to the
 * specified {@link AnnouncementFragment.OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class AnnouncementRecyclerViewAdapter extends RecyclerView.Adapter<AnnouncementRecyclerViewAdapter.ViewHolder> {

    private final List<AnnouncementItem> mValues;
    private final AnnouncementFragment.OnListFragmentInteractionListener mListener;

    public AnnouncementRecyclerViewAdapter(List<AnnouncementItem> items, AnnouncementFragment.OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_announcement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mTitleView.setText(holder.mItem.title);
        holder.mDateView.setText(String.format(holder.mView.getContext().getString(R.string.frag_announcement_posted_on), DateUtils.formatDateTime(holder.mView.getContext(), holder.mItem.pubDate.getTime(), DateUtils.FORMAT_SHOW_DATE)));
        holder.mDescriptionView.setText(holder.mItem.description);
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
        public final TextView mTitleView;
        public final TextView mDateView;
        public final TextView mDescriptionView;
        public AnnouncementItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTitleView = (TextView) view.findViewById(R.id.title_view);
            mDateView = (TextView) view.findViewById(R.id.date_view);
            mDescriptionView = (TextView) view.findViewById(R.id.description_view);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.title + "'";
        }
    }

    public static class AnnouncementItem implements Serializable {
        public final Date pubDate;
        public final String title;
        public final String description;
        public final String url;

        public AnnouncementItem(Date pubDate, String title, String description, String url) {
            this.pubDate = pubDate;
            this.title = title;
            this.description = description;
            this.url = url;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}

