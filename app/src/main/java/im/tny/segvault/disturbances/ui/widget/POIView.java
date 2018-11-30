package im.tny.segvault.disturbances.ui.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.POIActivity;
import im.tny.segvault.subway.POI;

public class POIView extends LinearLayout {
    private TextView nameView;
    private TextView secondNameView;
    private POI poi;

    public POIView(Context context, POI poi) {
        super(context);
        this.setOrientation(VERTICAL);
        this.poi = poi;
        initializeViews(context);
    }

    public POIView(Context context, AttributeSet attrs, POI poi) {
        super(context, attrs);
        this.setOrientation(VERTICAL);
        this.poi = poi;
        initializeViews(context);
    }

    public POIView(Context context, AttributeSet attrs, int defStyle, POI poi) {
        super(context, attrs, defStyle);
        this.setOrientation(VERTICAL);
        this.poi = poi;
        initializeViews(context);
    }

    /**
     * Inflates the views in the layout.
     *
     * @param context the current context for the view.
     */
    private void initializeViews(final Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.poi_view, this);

        Locale l = Util.getCurrentLocale(getContext());
        String lang = l.getLanguage();
        String[] names = poi.getNames(lang);

        nameView = findViewById(R.id.poi_name);
        secondNameView = findViewById(R.id.poi_second_name);
        nameView.setText(names[0]);
        if (names.length > 1) {
            secondNameView.setVisibility(VISIBLE);
            secondNameView.setText(names[1]);
        }
        OnClickListener clickListener = view12 -> {
            Intent intent = new Intent(getContext(), POIActivity.class);
            intent.putExtra(POIActivity.EXTRA_POI_ID, poi.getId());
            getContext().startActivity(intent);
        };
        nameView.setOnClickListener(clickListener);
        secondNameView.setOnClickListener(clickListener);

        findViewById(R.id.poi_map_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(String.format(
                            Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%f,%f",
                            poi.getWorldCoord()[0], poi.getWorldCoord()[1])));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // oh well
            }
        });

        findViewById(R.id.poi_website_button).setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(poi.getWebURL()));
            try {
                context.startActivity(browserIntent);
            } catch (ActivityNotFoundException e) {
                // oh well
            }
        });

        findViewById(R.id.poi_on_map_button).setOnClickListener(view1 -> {
            if (interactionListener != null) {
                interactionListener.onPOIClicked(poi);
            }
        });

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    private OnViewInteractionListener interactionListener;

    public OnViewInteractionListener getInteractionListener() {
        return interactionListener;
    }

    public void setInteractionListener(OnViewInteractionListener interactionListener) {
        this.interactionListener = interactionListener;
    }

    public interface OnViewInteractionListener {
        void onPOIClicked(POI poi);
    }
}
