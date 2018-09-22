package im.tny.segvault.disturbances.ui.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.Announcement;
import im.tny.segvault.disturbances.Connectivity;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.fragment.top.AnnouncementFragment;

/**
 * {@link RecyclerView.Adapter} that can display a {@link AnnouncementItem} and makes a call to the
 * specified {@link AnnouncementFragment.OnListFragmentInteractionListener}.
 */
public class AnnouncementRecyclerViewAdapter extends RecyclerView.Adapter<AnnouncementRecyclerViewAdapter.ViewHolder> {

    private final List<AnnouncementItem> mValues;
    private final AnnouncementFragment.OnListFragmentInteractionListener mListener;
    private Context context;

    public AnnouncementRecyclerViewAdapter(List<AnnouncementItem> items, AnnouncementFragment.OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.fragment_announcement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mTitleView.setText(holder.mItem.title);
        if (holder.mItem.title.isEmpty()) {
            holder.mTitleView.setVisibility(View.GONE);
        }
        holder.mDateView.setText(String.format(holder.mView.getContext().getString(R.string.frag_announcement_posted_on), DateUtils.formatDateTime(holder.mView.getContext(), holder.mItem.pubDate.getTime(), DateUtils.FORMAT_SHOW_DATE)));
        holder.mDescriptionView.setText(holder.mItem.description);
        int resId = android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Small;
        if (holder.mItem.description.length() > 0 && holder.mItem.description.length() <= 140) {
            resId = android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Medium;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            holder.mDescriptionView.setTextAppearance(resId);
        } else {
            holder.mDescriptionView.setTextAppearance(context, resId);
        }

        if (holder.mItem.title.isEmpty() && holder.mItem.description.isEmpty()) {
            holder.mDescriptionView.setText(R.string.frag_announcement_no_text);
            holder.mDescriptionView.setTypeface(null, Typeface.ITALIC);
        }

        if(!holder.mItem.imageURL.isEmpty()) {
            RequestCreator rc = Picasso.get().load(holder.mItem.imageURL);
             if (!Connectivity.isConnectedWifi(context)) {
                 rc.networkPolicy(NetworkPolicy.OFFLINE);
             }
            rc.into(holder.mImageView);
        } else {
            holder.mImageView.setVisibility(View.GONE);
        }

        holder.mSourceView.setImageResource(holder.mItem.source.drawableResourceId);

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
        public final ImageView mSourceView;
        public final ImageView mImageView;
        public final TextView mDescriptionView;
        public AnnouncementItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTitleView = view.findViewById(R.id.title_view);
            mDateView = view.findViewById(R.id.date_view);
            mImageView = view.findViewById(R.id.image_view);
            mSourceView = view.findViewById(R.id.source_view);
            mDescriptionView = view.findViewById(R.id.description_view);
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
        public final String imageURL;
        public final String url;
        public final Announcement.Source source;

        public AnnouncementItem(Date pubDate, String title, String description, String imageURL, String url, Announcement.Source source) {
            this.pubDate = pubDate;
            this.title = title;
            this.description = description;
            this.imageURL = imageURL;
            this.url = url;
            this.source = source;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}

