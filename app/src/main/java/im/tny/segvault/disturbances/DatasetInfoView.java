package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import im.tny.segvault.subway.Network;

public class DatasetInfoView extends LinearLayout {
    private TextView nameView;
    private TextView versionView;
    private TextView authorsView;
    private Button updateButton;
    private Network net;
    private AboutFragment containingFragment;

    public DatasetInfoView(Context context, Network net, AboutFragment aboutFragment) {
        super(context);
        this.setOrientation(VERTICAL);
        this.net = net;
        this.containingFragment = aboutFragment;
        initializeViews(context);
    }

    public DatasetInfoView(Context context, AttributeSet attrs, Network net, AboutFragment aboutFragment) {
        super(context, attrs);
        this.setOrientation(VERTICAL);
        this.net = net;
        this.containingFragment = aboutFragment;
        initializeViews(context);
    }

    public DatasetInfoView(Context context,
                           AttributeSet attrs,
                           int defStyle, Network net, AboutFragment aboutFragment) {
        super(context, attrs, defStyle);
        this.setOrientation(VERTICAL);
        this.net = net;
        this.containingFragment = aboutFragment;
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
        inflater.inflate(R.layout.dataset_info_view, this);

        nameView = (TextView) findViewById(R.id.dataset_info_name);
        versionView = (TextView) findViewById(R.id.dataset_info_version);
        authorsView = (TextView) findViewById(R.id.dataset_info_authors);
        updateButton = (Button) findViewById(R.id.dataset_update_button);

        nameView.setText(net.getName());
        versionView.setText(String.format(context.getString(R.string.dataset_info_version), net.getDatasetVersion()));

        String authors = "";
        for (String author : net.getDatasetAuthors()) {
            authors += author + ", ";
        }
        authorsView.setText(authors.substring(0, authors.length() - 2));

        updateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                containingFragment.updateNetworks(net.getId());
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_CANCELLED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(containingFragment.getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS:
                    updateButton.setEnabled(false);
                    break;
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                case MainService.ACTION_UPDATE_TOPOLOGY_CANCELLED:
                    updateButton.setEnabled(true);
                    break;
            }
        }
    };
}
