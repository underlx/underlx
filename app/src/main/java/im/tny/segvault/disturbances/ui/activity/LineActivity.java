package im.tny.segvault.disturbances.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.top.RouteFragment;
import im.tny.segvault.disturbances.ui.util.CustomFAB;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;

public class LineActivity extends TopActivity {

    private String networkId;
    private String lineId;

    private LinearLayout lineIconsLayout;
    private LinearLayout warningLayout;
    private TextView warningView;
    private LinearLayout lineLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            networkId = getIntent().getStringExtra(EXTRA_NETWORK_ID);
            lineId = getIntent().getStringExtra(EXTRA_LINE_ID);
        } else {
            networkId = savedInstanceState.getString(STATE_NETWORK_ID);
            lineId = savedInstanceState.getString(STATE_LINE_ID);
        }

        setContentView(R.layout.activity_line);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        CustomFAB fab = findViewById(R.id.fab);
        fab.hide();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        lineIconsLayout = findViewById(R.id.line_icons_layout);
        warningLayout = findViewById(R.id.warning_layout);
        warningView = findViewById(R.id.warning_view);
        lineLayout = findViewById(R.id.line_layout);

        Network net = Coordinator.get(this).getMapManager().getNetwork(networkId);
        if (net == null) {
            finish();
            return;
        }
        Line line = net.getLine(lineId);
        if (line == null) {
            finish();
            return;
        }

        String title = String.format(getString(R.string.act_line_title), Util.getLineNames(LineActivity.this, line)[0]);
        setTitle(title);
        getSupportActionBar().setTitle(title);
        AppBarLayout abl = findViewById(R.id.app_bar);
        final CollapsingToolbarLayout ctl = findViewById(R.id.toolbar_layout);
        ctl.setTitle(title);

        int color = line.getColor();
        ctl.setContentScrimColor(color);
        ctl.setStatusBarScrimColor(color);
        abl.setBackgroundColor(color);

        Drawable drawable = ContextCompat.getDrawable(LineActivity.this, Util.getDrawableResourceIdForLineId(line.getId()));
        drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics());
        FrameLayout iconFrame = new FrameLayout(LineActivity.this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(height, height);
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            params.setMarginEnd(margin);
        }
        params.setMargins(0, 0, margin, 0);
        iconFrame.setLayoutParams(params);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            iconFrame.setBackgroundDrawable(drawable);
        } else {
            iconFrame.setBackground(drawable);
        }
        lineIconsLayout.addView(iconFrame);

        abl.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (ctl.getHeight() + verticalOffset < 2.5 * ViewCompat.getMinimumHeight(ctl)) {
                lineIconsLayout.animate().alpha(0);
            } else {
                lineIconsLayout.animate().alpha(1);
            }
        });

        Map<String, LineStatusCache.Status> statuses = Coordinator.get(this).getLineStatusCache().getLineStatus();
        LineStatusCache.Status lineStatus = statuses.get(line.getId());
        if (lineStatus != null && new Date().getTime() - lineStatus.updated.getTime() < java.util.concurrent.TimeUnit.MINUTES.toMillis(5) && lineStatus.down) {
            warningView.setText(R.string.act_line_disturbances_warning);
            warningLayout.setVisibility(View.VISIBLE);
        } else {
            warningLayout.setVisibility(View.GONE);
        }

        LinearLayout infoLayout = findViewById(R.id.info_layout);
        TextView infoView = findViewById(R.id.info_view);
        if (line.isExceptionallyClosed(new Date())) {
            Formatter f = new Formatter();
            DateUtils.formatDateRange(LineActivity.this, f, line.getNextOpenTime(), line.getNextOpenTime(), DateUtils.FORMAT_SHOW_TIME, net.getTimezone().getID());
            infoView.setText(String.format(getString(R.string.act_line_closed_schedule), f.toString()));
            infoLayout.setVisibility(View.VISIBLE);
        } else if (lineStatus != null && lineStatus.condition.trainCars < line.getUsualCarCount() && lineStatus.condition.trainCars > 0) {
            infoView.setText(String.format(getString(R.string.act_line_short_cars_now_warning),
                    lineStatus.condition.trainCars, line.getUsualCarCount()));
            infoLayout.setVisibility(View.VISIBLE);
        } else {
            infoLayout.setVisibility(View.GONE);
        }

        populateLineView(LineActivity.this, getLayoutInflater(), line, lineLayout);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.line, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_share_webprofile:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, getString(R.string.link_format_line), lineId));
                try {
                    startActivity(shareIntent);
                } catch (ActivityNotFoundException e) {
                    // oh well
                }
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    public static void populateLineView(final Context context, final LayoutInflater inflater, final Line line, ViewGroup root) {
        root.removeAllViews();

        List<Station> stations = line.getStationsInOrder();

        int lineColor = line.getColor();

        for (int i = 0; i < stations.size(); i++) {
            final Station station = stations.get(i);

            View stepview = inflater.inflate(R.layout.path_station, root, false);

            TextView timeView = stepview.findViewById(R.id.time_view);
            timeView.setVisibility(View.INVISIBLE);

            FrameLayout prevLineStripeLayout = stepview.findViewById(R.id.prev_line_stripe_layout);
            FrameLayout nextLineStripeLayout = stepview.findViewById(R.id.next_line_stripe_layout);
            FrameLayout backLineStripeLayout = stepview.findViewById(R.id.back_line_stripe_layout);
            FrameLayout centerLineStripeLayout = stepview.findViewById(R.id.center_line_stripe_layout);
            centerLineStripeLayout.setVisibility(View.VISIBLE);
            FrameLayout leftLineStripeLayout = stepview.findViewById(R.id.left_line_stripe_layout);
            FrameLayout rightLineStripeLayout = stepview.findViewById(R.id.right_line_stripe_layout);

            if (i == 0) {
                prevLineStripeLayout.setVisibility(View.INVISIBLE);
                nextLineStripeLayout.setBackgroundColor(lineColor);

                Drawable background = Util.getColoredDrawableResource(context, R.drawable.station_line_top, lineColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    centerLineStripeLayout.setBackground(background);
                } else {
                    centerLineStripeLayout.setBackgroundDrawable(background);
                }
            } else if (i == stations.size() - 1) {
                prevLineStripeLayout.setBackgroundColor(lineColor);
                nextLineStripeLayout.setVisibility(View.INVISIBLE);

                Drawable background = Util.getColoredDrawableResource(context, R.drawable.station_line_bottom, lineColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    centerLineStripeLayout.setBackground(background);
                } else {
                    centerLineStripeLayout.setBackgroundDrawable(background);
                }
            } else {
                prevLineStripeLayout.setBackgroundColor(lineColor);
                nextLineStripeLayout.setBackgroundColor(lineColor);
                centerLineStripeLayout.setBackgroundColor(lineColor);
            }

            int rightColor = 0;
            int leftColor = 0;
            if (station.getLines().size() > 1) {
                for (Stop stop : station.getStops()) {
                    if (stop.getLine() != line) {
                        rightColor = stop.getLine().getColor();
                        if (stop.getLine().outDegreeOf(stop) > 1) {
                            leftColor = stop.getLine().getColor();
                        }
                        break;
                    }
                }
            }

            if (rightColor != 0) {
                rightLineStripeLayout.setVisibility(View.VISIBLE);

                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.RIGHT_LEFT,
                        new int[]{0, rightColor});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    rightLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    rightLineStripeLayout.setBackground(gd);
                }
            }
            if (leftColor != 0) {
                leftLineStripeLayout.setVisibility(View.VISIBLE);
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0, leftColor});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    leftLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    leftLineStripeLayout.setBackground(gd);
                }
            }

            // fill gap left by rounded corners of top/bottom
            if ((leftColor != 0 || rightColor != 0) && (i == 0 || i == stations.size() - 1)) {
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{leftColor, rightColor});
                gd.setCornerRadius(0f);

                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    backLineStripeLayout.setBackgroundDrawable(gd);
                } else {
                    backLineStripeLayout.setBackground(gd);
                }
                backLineStripeLayout.setVisibility(View.VISIBLE);
            }

            stepview.setOnClickListener(view -> {
                Intent intent = new Intent(context, StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, station.getId());
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, station.getNetwork().getId());
                context.startActivity(intent);
            });

            RouteFragment.populateStationView(context, station, stepview, true, false);
            root.addView(stepview);
        }
    }

    public static final String STATE_LINE_ID = "lineId";
    public static final String STATE_NETWORK_ID = "networkId";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_LINE_ID, lineId);
        savedInstanceState.putString(STATE_NETWORK_ID, networkId);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_LINE_ID = "im.tny.segvault.disturbances.extra.LineActivity.lineid";
    public static final String EXTRA_NETWORK_ID = "im.tny.segvault.disturbances.extra.LineActivity.networkid";

}
