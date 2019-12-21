package im.tny.segvault.disturbances.ui.activity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.widget.Toolbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.StationUse;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.ui.fragment.TripFragment;
import im.tny.segvault.disturbances.ui.widget.StationPickerView;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;

public class TripCorrectionActivity extends TopActivity {

    private String networkId;
    private String tripId;
    private boolean isStandalone;

    private Network network;

    private StationPickerView startPicker;
    private StationPickerView endPicker;
    private LinearLayout pathLayout;
    private LinearLayout buttonsLayout;
    private Button saveButton;
    private MenuItem saveMenuItem;

    private Trip trip;
    private List<StationUse> uses;
    private Path originalPath;
    private Path newPath;
    private boolean partsDeleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            networkId = getIntent().getStringExtra(EXTRA_NETWORK_ID);
            tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
            isStandalone = getIntent().getBooleanExtra(EXTRA_IS_STANDALONE, false);
        } else {
            networkId = savedInstanceState.getString(STATE_NETWORK_ID);
            tripId = savedInstanceState.getString(STATE_TRIP_ID);
            isStandalone = savedInstanceState.getBoolean(STATE_IS_STANDALONE, false);
        }

        setContentView(R.layout.activity_trip_correction);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (isStandalone) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        }

        startPicker = findViewById(R.id.start_picker);
        endPicker = findViewById(R.id.end_picker);
        pathLayout = findViewById(R.id.path_layout);
        buttonsLayout = findViewById(R.id.buttons_layout);
        saveButton = findViewById(R.id.save_button);

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(STATE_START_FOCUSED, false)) {
                startPicker.focusOnEntry();
            }
            if (savedInstanceState.getBoolean(STATE_END_FOCUSED, false)) {
                endPicker.focusOnEntry();
            }
        }

        network = Coordinator.get(this).getMapManager().getNetwork(networkId);

        new LoadTripTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_correction, menu);
        saveMenuItem = menu.findItem(R.id.menu_save);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.menu_save:
                saveChanges();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void redrawPath() {
        newPath = new Path(originalPath);
        boolean hasChanges = partsDeleted;
        if (startPicker.getSelection() != null && !startPicker.getSelection().isAlwaysClosed() && startPicker.getSelection() != originalPath.getStartVertex().getStation()) {
            newPath.manualExtendStart(startPicker.getSelection().getStops().iterator().next());
            hasChanges = true;
        }
        if (endPicker.getSelection() != null && !endPicker.getSelection().isAlwaysClosed() && endPicker.getSelection() != originalPath.getEndVertex().getStation()) {
            newPath.manualExtendEnd(endPicker.getSelection().getStops().iterator().next());
            hasChanges = true;
        }

        TripFragment.populatePathView(this, getLayoutInflater(), newPath, pathLayout, false);

        final ViewTreeObserver vto = pathLayout.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    buttonsLayout.removeAllViews();

                    int manualOffset = 0;
                    for (int i = 0; i < pathLayout.getChildCount(); i++) {
                        View stepview = getLayoutInflater().inflate(R.layout.path_button, buttonsLayout, false);

                        if (newPath.getManualEntry(i)) {
                            manualOffset++;
                        } else {
                            final int idxOnTrip = i - manualOffset;
                            if (canRemoveVertex(idxOnTrip)) {
                                stepview.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
                                stepview.findViewById(R.id.delete_button).setOnClickListener(v -> removeVertex(idxOnTrip));
                            }
                        }

                        stepview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, pathLayout.getChildAt(i).getHeight()));

                        buttonsLayout.addView(stepview);
                    }
                    // remove the listener... or we'll be doing this a lot.
                    ViewTreeObserver obs = pathLayout.getViewTreeObserver();
                    obs.removeGlobalOnLayoutListener(this);
                }
            });
        }

        if (hasChanges) {
            saveButton.setText(getString(R.string.act_trip_correction_save));
        } else {
            saveButton.setText(getString(R.string.act_trip_correction_correct));
        }
    }

    private boolean canRemoveVertex(int indexToRemove) {
        if (indexToRemove <= 0 || indexToRemove >= uses.size() - 1) {
            return false;
        }
        return uses.get(indexToRemove - 1).stationID.equals(uses.get(indexToRemove + 1).stationID)
                && uses.get(indexToRemove).type == StationUse.UseType.GONE_THROUGH;
    }

    private void removeVertex(int i) {
        if (!canRemoveVertex(i)) {
            return;
        }
        uses.get(i - 1).leaveDate = uses.get(i + 1).leaveDate;
        uses.remove(i);
        // the next one to remove might be an interchange, if yes, save its src/dest line as it may be needed later
        String srcLineId = uses.get(i).sourceLine;
        String dstLineId = uses.get(i).targetLine;
        uses.remove(i);
        if (uses.size() == 1) {
            uses.get(0).type = StationUse.UseType.VISIT;
        } else {
            uses.get(0).type = StationUse.UseType.NETWORK_ENTRY;
            uses.get(uses.size() - 1).type = StationUse.UseType.NETWORK_EXIT;

            if (i < uses.size() && i > 1) {
                Station src = network.getStation(uses.get(i - 2).stationID);
                Station dst = network.getStation(uses.get(i).stationID);

                boolean foundSameLine = false;
                for (Stop srcStop : src.getStops()) {
                    for (Line dstLine : dst.getLines()) {
                        if (dstLine.containsVertex(srcStop)) {
                            foundSameLine = true;
                        }
                    }
                }
                if (!foundSameLine) {
                    if (uses.get(i - 1).type != StationUse.UseType.INTERCHANGE) {
                        uses.get(i - 1).type = StationUse.UseType.INTERCHANGE;
                        uses.get(i - 1).sourceLine = srcLineId;
                        uses.get(i - 1).targetLine = dstLineId;
                    }
                } else {
                    uses.get(i - 1).type = StationUse.UseType.GONE_THROUGH;
                }
            }
        }
        partsDeleted = true;
        redrawPath();
    }

    private void saveChanges() {
        new SaveChangesTask().execute();
    }

    private static class LoadTripTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<TripCorrectionActivity> parentRef;

        LoadTripTask(TripCorrectionActivity activity) {
            this.parentRef = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            TripCorrectionActivity parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent).getDB();

            parent.trip = db.tripDao().get(parent.tripId);
            if (parent.trip == null) {
                return false;
            }

            parent.uses = db.stationUseDao().getOfTrip(parent.tripId);

            parent.originalPath = parent.trip.toConnectionPath(db, parent.network);

            if (parent.originalPath == null || !parent.trip.canBeCorrected(db)) {
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            TripCorrectionActivity parent = parentRef.get();
            if (parent == null) {
                return;
            }
            if (!result) {
                parent.finish();
                return;
            }

            List<Station> stations = new ArrayList<>(parent.network.getStations());

            parent.startPicker.setStations(stations);
            parent.endPicker.setStations(stations);

            parent.startPicker.setAllStationsSortStrategy(new StationPickerView.DistanceSortStrategy(parent.network, parent.originalPath.getStartVertex()));
            parent.endPicker.setAllStationsSortStrategy(new StationPickerView.DistanceSortStrategy(parent.network, parent.originalPath.getEndVertex()));

            parent.startPicker.setWeakSelection(parent.originalPath.getStartVertex().getStation());
            parent.endPicker.setWeakSelection(parent.originalPath.getEndVertex().getStation());

            StationPickerView.OnStationSelectedListener onStationSelectedListener = station -> parent.redrawPath();
            parent.startPicker.setOnStationSelectedListener(onStationSelectedListener);
            parent.endPicker.setOnStationSelectedListener(onStationSelectedListener);

            StationPickerView.OnSelectionLostListener onSelectionLostListener = () -> parent.redrawPath();
            parent.startPicker.setOnSelectionLostListener(onSelectionLostListener);
            parent.endPicker.setOnSelectionLostListener(onSelectionLostListener);

            parent.redrawPath();

            parent.saveButton.setOnClickListener(v -> parent.saveChanges());
        }
    }

    private class SaveChangesTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            saveButton.setEnabled(false);
            saveMenuItem.setEnabled(false);
            Util.setViewAndChildrenEnabled(buttonsLayout, false);
            Util.setViewAndChildrenEnabled(startPicker, false);
            Util.setViewAndChildrenEnabled(endPicker, false);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Trip.persistConnectionPath(Coordinator.get(TripCorrectionActivity.this).getDB(), newPath, tripId);
            return null;
        }

        @Override
        protected void onPostExecute(Void nothing) {
            super.onPostExecute(nothing);

            finish();
        }
    }

    public static final String STATE_TRIP_ID = "tripId";
    public static final String STATE_NETWORK_ID = "networkId";
    public static final String STATE_IS_STANDALONE = "standalone";
    public static final String STATE_START_FOCUSED = "startFocused";
    public static final String STATE_END_FOCUSED = "endFocused";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_TRIP_ID, tripId);
        savedInstanceState.putString(STATE_NETWORK_ID, networkId);
        savedInstanceState.putBoolean(STATE_START_FOCUSED, startPicker.isFocused());
        savedInstanceState.putBoolean(STATE_END_FOCUSED, endPicker.isFocused());
        savedInstanceState.putBoolean(STATE_IS_STANDALONE, isStandalone);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_TRIP_ID = "im.tny.segvault.disturbances.extra.TripCorrectionActivity.tripid";
    public static final String EXTRA_NETWORK_ID = "im.tny.segvault.disturbances.extra.TripCorrectionActivity.networkid";
    public static final String EXTRA_IS_STANDALONE = "im.tny.segvault.disturbances.extra.TripCorrectionActivity.standalone";
}
