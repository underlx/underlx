package im.tny.segvault.disturbances.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.LineStatusCache;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

public class ReportActivity extends TopActivity {
    private LinearLayout linesLayout;
    private Button sendButton;

    private boolean isStandalone;
    private Set<Line> checkedLines = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            isStandalone = getIntent().getBooleanExtra(EXTRA_IS_STANDALONE, false);
        } else {
            isStandalone = savedInstanceState.getBoolean(STATE_IS_STANDALONE, false);
        }

        setContentView(R.layout.activity_report);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (isStandalone) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        linesLayout = findViewById(R.id.lines_layout);
        sendButton = findViewById(R.id.send_button);
        sendButton.setOnClickListener(view -> new SubmitReportTask().executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR));

        populateLineList(false);
    }

    private class SubmitReportTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Util.setViewAndChildrenEnabled(linesLayout, false);
            sendButton.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean anySuccess = false;
            for (Line line : checkedLines) {
                API.DisturbanceReport report = new API.DisturbanceReport();
                report.category = "generic";
                report.line = line.getId();

                try {
                    API.getInstance().postDisturbanceReport(report);
                    anySuccess = true;
                } catch (APIException ex) {
                    // oh well
                }
            }
            return anySuccess;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            String toastText = getString(R.string.act_report_success);
            if (!success) {
                toastText = getString(R.string.act_report_error);
            }

            if (isStandalone && success) {
                // make normal toast
                Toast.makeText(ReportActivity.this, toastText, Toast.LENGTH_LONG);
            } else if (!success) {
                // display fancy toast
                Snackbar.make(linesLayout, toastText, 5000).show();
            } else {
                // tell main activity to display fancy toast
                Intent intent = new Intent(ACTION_REPORT_PROVIDED);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(ReportActivity.this);
                bm.sendBroadcast(intent);
            }

            if (success) {
                finish();
            } else {
                Util.setViewAndChildrenEnabled(linesLayout, true);
                sendButton.setEnabled(true);
            }
        }
    }

    private void populateLineList(boolean fromUpdate) {
        List<Line> lines = new ArrayList<>();
        for (Network network : Coordinator.get(this).getMapManager().getNetworks()) {
            List<Line> nLines = new ArrayList<>(network.getLines());
            Collections.sort(nLines, (t0, t1) -> Integer.valueOf(t0.getOrder()).compareTo(t1.getOrder()));
            lines.addAll(nLines);
        }

        linesLayout.removeAllViews();
        boolean needsStatusUpdate = false;
        for (final Line line : lines) {
            View view = getLayoutInflater().inflate(R.layout.checkbox_report_line, linesLayout, false);
            final CheckBox lineCheckbox = view.findViewById(R.id.line_checkbox);
            lineCheckbox.setChecked(checkedLines.contains(line));
            lineCheckbox.setOnCheckedChangeListener((compoundButton, b) -> {
                if (b) {
                    checkedLines.add(line);
                } else {
                    checkedLines.remove(line);
                }
                sendButton.setEnabled(checkedLines.size() > 0);
            });
            TextView lineNameView = view.findViewById(R.id.line_name_view);
            String lineName = Util.getLineNames(ReportActivity.this, line)[0];
            String lineLine = String.format(getString(R.string.act_report_line_name), lineName);
            S2LS s2ls = Coordinator.get(this).getS2LS(line.getNetwork().getId());
            if (s2ls != null && s2ls.getCurrentTrip() != null) {
                final Station station = s2ls.getCurrentTrip().getCurrentStop().getStation();
                if (station.getLines().contains(line)) {
                    lineLine += " " + getString(R.string.act_report_you_are_here);
                }
            }
            int lStart = lineLine.indexOf(lineName);
            int lEnd = lStart + lineName.length();
            SpannableString nameSpannable = new SpannableString(lineLine);
            nameSpannable.setSpan(new ForegroundColorSpan(line.getColor()), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            lineNameView.setText(nameSpannable);
            View.OnClickListener cbOnClick = view1 -> lineCheckbox.performClick();

            TextView lineClosedView = view.findViewById(R.id.line_closed_view);
            if (!line.isOpen()) {
                lineClosedView.setVisibility(View.VISIBLE);
                lineCheckbox.setEnabled(false);
                lineNameView.setTextColor(Color.LTGRAY);
            } else {
                lineNameView.setOnClickListener(cbOnClick);
                lineClosedView.setOnClickListener(cbOnClick);
                view.setOnClickListener(cbOnClick);
            }

            Map<String, LineStatusCache.Status> statuses = Coordinator.get(this).getLineStatusCache().getLineStatus();
            LineStatusCache.Status lineStatus = statuses.get(line.getId());
            TextView setFrequencyView = view.findViewById(R.id.line_set_frequency_view);
            if (lineStatus != null && new Date().getTime() - lineStatus.updated.getTime() < java.util.concurrent.TimeUnit.MINUTES.toMillis(5)) {
                if (lineStatus.condition.trainFrequency > 0) {
                    setFrequencyView.setText(String.format(getString(R.string.act_report_line_train_frequency), DateUtils.formatElapsedTime(lineStatus.condition.trainFrequency / 1000)));
                    setFrequencyView.setVisibility(View.VISIBLE);
                }
            } else {
                needsStatusUpdate = true;
            }
            linesLayout.addView(view);
        }
        sendButton.setEnabled(checkedLines.size() > 0);

        if (needsStatusUpdate && !fromUpdate) {
            Coordinator.get(this).getLineStatusCache().updateLineStatus();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LineStatusCache.ACTION_LINE_STATUS_UPDATE_SUCCESS:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    populateLineList(true);
                    break;
            }
        }
    };

    public static final String STATE_IS_STANDALONE = "standalone";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(STATE_IS_STANDALONE, isStandalone);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_IS_STANDALONE = "im.tny.segvault.disturbances.extra.ReportActivity.standalone";

    public static final String ACTION_REPORT_PROVIDED = "im.tny.segvault.disturbances.action.ReportActivity.provided";
}
