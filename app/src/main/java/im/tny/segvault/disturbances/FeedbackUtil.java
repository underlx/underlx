package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;

import android.os.AsyncTask;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.Feedback;
import im.tny.segvault.disturbances.ui.widget.StationPickerView;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 9/19/17.
 */

public class FeedbackUtil {
    public static class IncorrectLocation {
        private Context context;

        @Nullable
        private Station incorrectStation = null;

        private List<ScanResult> scanResults;

        public IncorrectLocation(Context context, Station incorrectStation) {
            this(context);
            this.incorrectStation = incorrectStation;
        }

        public IncorrectLocation(Context context) {
            this.context = context;
            scanResults = Coordinator.get(context).getLastWiFiScanResults();
        }

        public void showReportWizard() {
            if (incorrectStation == null) {
                // it makes no sense to not be at a station and send a report for not being at a station
                // so, let's assume the user is at a station and skip the first step
                askCurrentStation();
                return;
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.feedback_location_title)
                    .setMessage(R.string.feedback_location_where_are_you)
                    .setPositiveButton(R.string.feedback_location_in_subway, (dialog, whichButton) -> askCurrentStation())
                    .setNegativeButton(R.string.feedback_location_outside,
                            (dialog, whichButton) ->
                                    new StoreFeedbackTask(context, incorrectStation, null).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR)).show();
        }

        private void askCurrentStation() {
            final StationPickerView spv = new StationPickerView(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = context.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            params.rightMargin = context.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            spv.setLayoutParams(params);
            FrameLayout container = new FrameLayout(context);
            // avoid autofocus on StationPickerView, which immediately opens the dropdown
            container.setFocusableInTouchMode(true);
            container.addView(spv);
            spv.setHint(context.getString(R.string.feedback_location_hint));
            spv.setStations(Coordinator.get(context).getMapManager().getAllStations());
            spv.setAllStationsSortStrategy(new StationPickerView.AZSortStrategy());

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.feedback_location_title)
                            .setMessage(R.string.feedback_location_select_station)
                            .setPositiveButton(R.string.feedback_location_action_select, (dialog, which) -> {
                                if (spv.getSelection() != null) {
                                    new StoreFeedbackTask(context, incorrectStation, spv.getSelection()).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
                                } else {
                                    askCurrentStation();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                            .setView(container);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialog1 -> ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(spv.getSelection() != null));
            spv.setOnStationSelectedListener(station -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            spv.setOnSelectionLostListener(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));

            dialog.show();
        }

        private static class StoreFeedbackTask extends AsyncTask<Void, Void, Void> {
            private WeakReference<Context> contextRef;

            @Nullable
            private Station incorrectStation = null;

            @Nullable
            private Station correctStation = null;

            StoreFeedbackTask(Context context,
                              @Nullable Station incorrectStation,
                              @Nullable Station correctStation) {
                this.contextRef = new WeakReference<>(context);
                this.incorrectStation = incorrectStation;
                this.correctStation = correctStation;
            }

            @Override
            protected Void doInBackground(Void... voids) {
                Context context = contextRef.get();
                if (context == null) {
                    return null;
                }

                // build report
                Map<String, Object> map = new HashMap<>();
                if (incorrectStation == null) {
                    map.put("incorrectStation", "none");
                } else {
                    map.put("incorrectStation", incorrectStation.getId());
                }
                if (correctStation == null) {
                    map.put("correctStation", "none");
                } else {
                    map.put("correctStation", correctStation.getId());
                }
                map.put("wiFiScanResults", Coordinator.get(context).getLastWiFiScanResults());

                ObjectMapper mapper = new ObjectMapper();
                AppDatabase db = Coordinator.get(context).getDB();

                try {
                    String jsonResult = mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(map);
                    db.runInTransaction(() -> {
                        Feedback feedback = new Feedback();
                        feedback.id = UUID.randomUUID().toString();
                        feedback.synced = false;
                        feedback.timestamp = new Date();
                        feedback.type = "s2ls-incorrect-detection";
                        feedback.contents = jsonResult;
                        db.feedbackDao().insertAll(feedback);
                    });

                    Intent intent = new Intent(ACTION_FEEDBACK_PROVIDED);
                    intent.putExtra(EXTRA_FEEDBACK_PROVIDED_DELAYED, !Connectivity.isConnected(context));
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                    bm.sendBroadcast(intent);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }
    }

    public static final String ACTION_FEEDBACK_PROVIDED = "im.tny.segvault.disturbances.action.feedback.provided";
    public static final String EXTRA_FEEDBACK_PROVIDED_DELAYED = "im.tny.segvault.disturbances.extra.feedback.provided.delayed";
}
