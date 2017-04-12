package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

public class DatasetInfoView extends LinearLayout {
    private TextView nameView;
    private TextView versionView;
    private TextView authorsView;
    private Network net;

    public DatasetInfoView(Context context, Network net) {
        super(context);
        this.setOrientation(VERTICAL);
        this.net = net;
        initializeViews(context);
    }

    public DatasetInfoView(Context context, AttributeSet attrs, Network net) {
        super(context, attrs);
        this.setOrientation(VERTICAL);
        this.net = net;
        initializeViews(context);
    }

    public DatasetInfoView(Context context,
                       AttributeSet attrs,
                       int defStyle, Network net) {
        super(context, attrs, defStyle);
        this.setOrientation(VERTICAL);
        this.net = net;
        initializeViews(context);
    }

    /**
     * Inflates the views in the layout.
     *
     * @param context
     *           the current context for the view.
     */
    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.dataset_info_view, this);

        nameView = (TextView)findViewById(R.id.dataset_info_name);
        versionView = (TextView)findViewById(R.id.dataset_info_version);
        authorsView = (TextView) findViewById(R.id.dataset_info_authors);

        nameView.setText(net.getName());
        versionView.setText(String.format("Version: %s", net.getDatasetVersion()));

        String authors = "";
        for(String author : net.getDatasetAuthors()) {
            authors += author + "\n";
        }
        authorsView.setText(authors.trim());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }
}
