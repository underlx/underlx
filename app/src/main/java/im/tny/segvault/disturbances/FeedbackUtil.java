package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.ScanResult;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import im.tny.segvault.disturbances.model.Feedback;
import im.tny.segvault.subway.Station;
import io.realm.Realm;

/**
 * Created by gabriel on 9/19/17.
 */

public class FeedbackUtil {
    public static class IncorrectLocation {
        private Context context;
        private MainService mService;
        private Station incorrectStation;
        private List<ScanResult> scanResults;

        public IncorrectLocation(Context context, MainService mService, Station incorrectStation) {
            this.context = context;
            this.mService = mService;
            this.incorrectStation = incorrectStation;
            scanResults = mService.getLastWiFiScanResults();
        }

        public void showReportWizard() {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.feedback_location_title)
                    .setMessage(R.string.feedback_location_where_are_you)
                    .setPositiveButton(R.string.feedback_location_in_subway, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            askCurrentStation();
                        }
                    })
                    .setNegativeButton(R.string.feedback_location_outside, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            sendReport(null);
                        }
                    }).show();
        }

        private void askCurrentStation() {
            final StationPickerView spv = new StationPickerView(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = context.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            params.rightMargin = context.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            spv.setLayoutParams(params);
            FrameLayout container = new FrameLayout(context);
            container.addView(spv);

            spv.setStations(mService.getAllStations());
            spv.setAllStationsSortStrategy(new StationPickerView.AZSortStrategy());

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.feedback_location_title)
                            .setMessage(R.string.feedback_location_select_station)
                            .setPositiveButton(R.string.feedback_location_action_select, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (spv.getSelection() != null) {
                                        sendReport(spv.getSelection());
                                    } else {
                                        askCurrentStation();
                                    }
                                }
                            })
                            .setView(container);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(spv.getSelection() != null);
                }
            });
            spv.setOnStationSelectedListener(new StationPickerView.OnStationSelectedListener() {
                @Override
                public void onStationSelected(Station station) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
            spv.setOnSelectionLostListener(new StationPickerView.OnSelectionLostListener() {
                @Override
                public void onSelectionLost() {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            });

            dialog.show();
        }

        private void sendReport(@Nullable Station correctStation) {
            // build report
            Map<String, Object> map = new HashMap<>();
            map.put("incorrectStation", incorrectStation.getId());
            if (correctStation == null) {
                map.put("correctStation", "none");
            } else {
                map.put("correctStation", correctStation.getId());
            }
            map.put("wiFiScanResults", scanResults);

            ObjectMapper mapper = new ObjectMapper();
            try {
                String jsonResult = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(map);
                Realm realm = Realm.getDefaultInstance();
                realm.beginTransaction();
                Feedback feedback = realm.createObject(Feedback.class, UUID.randomUUID().toString());
                feedback.setSynced(false);
                feedback.setTimestamp(new Date());
                feedback.setType("s2ls-incorrect-detection");
                feedback.setContents(jsonResult);
                realm.copyToRealm(feedback);
                realm.commitTransaction();
                realm.close();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }
}
