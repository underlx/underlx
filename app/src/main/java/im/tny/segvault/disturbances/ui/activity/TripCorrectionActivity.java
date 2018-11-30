package im.tny.segvault.disturbances.ui.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.widget.StationPickerView;
import im.tny.segvault.disturbances.ui.fragment.TripFragment;
import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import io.realm.Realm;

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

        populateUI();
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

    private void populateUI() {
        Realm realm = Application.getDefaultRealmInstance(this);
        trip = realm.where(Trip.class).equalTo("id", tripId).findFirst();
        if (trip == null) {
            finish();
            return;
        }
        trip = realm.copyFromRealm(trip);
        realm.close();

        originalPath = trip.toConnectionPath(network);

        if (!trip.canBeCorrected()) {
            finish();
            return;
        }

        List<Station> stations = new ArrayList<>(network.getStations());

        startPicker.setStations(stations);
        endPicker.setStations(stations);

        startPicker.setAllStationsSortStrategy(new StationPickerView.DistanceSortStrategy(network, originalPath.getStartVertex()));
        endPicker.setAllStationsSortStrategy(new StationPickerView.DistanceSortStrategy(network, originalPath.getEndVertex()));

        startPicker.setWeakSelection(originalPath.getStartVertex().getStation());
        endPicker.setWeakSelection(originalPath.getEndVertex().getStation());

        StationPickerView.OnStationSelectedListener onStationSelectedListener = station -> redrawPath();
        startPicker.setOnStationSelectedListener(onStationSelectedListener);
        endPicker.setOnStationSelectedListener(onStationSelectedListener);

        StationPickerView.OnSelectionLostListener onSelectionLostListener = () -> redrawPath();
        startPicker.setOnSelectionLostListener(onSelectionLostListener);
        endPicker.setOnSelectionLostListener(onSelectionLostListener);

        redrawPath();

        saveButton.setOnClickListener(v -> saveChanges());
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

                        if(newPath.getManualEntry(i)) {
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
        List<StationUse> uses = trip.getPath();
        if (indexToRemove <= 0 || indexToRemove >= uses.size() - 1) {
            return false;
        }
        return uses.get(indexToRemove - 1).getStation().getId().equals(uses.get(indexToRemove + 1).getStation().getId())
                && uses.get(indexToRemove).getType() == StationUse.UseType.GONE_THROUGH;
    }

    private void removeVertex(int i) {
        if (!canRemoveVertex(i)) {
            return;
        }
        List<StationUse> uses = trip.getPath();
        uses.get(i - 1).setLeaveDate(trip.getPath().get(i + 1).getLeaveDate());
        uses.remove(i);
        // the next one to remove might be an interchange, if yes, save its src/dest line as it may be needed later
        String srcLineId = uses.get(i).getSourceLine();
        String dstLineId = uses.get(i).getTargetLine();
        uses.remove(i);
        if (uses.size() == 1) {
            uses.get(0).setType(StationUse.UseType.VISIT);
        } else {
            uses.get(0).setType(StationUse.UseType.NETWORK_ENTRY);
            uses.get(trip.getPath().size() - 1).setType(StationUse.UseType.NETWORK_EXIT);

            if (i < uses.size() && i > 1) {
                Station src = network.getStation(uses.get(i - 2).getStation().getId());
                Station dst = network.getStation(uses.get(i).getStation().getId());

                boolean foundSameLine = false;
                for (Stop srcStop : src.getStops()) {
                    for (Line dstLine : dst.getLines()) {
                        if (dstLine.containsVertex(srcStop)) {
                            foundSameLine = true;
                        }
                    }
                }
                if (!foundSameLine) {
                    if (uses.get(i - 1).getType() != StationUse.UseType.INTERCHANGE) {
                        uses.get(i - 1).setType(StationUse.UseType.INTERCHANGE);
                        uses.get(i - 1).setSourceLine(srcLineId);
                        uses.get(i - 1).setTargetLine(dstLineId);
                    }
                } else {
                    uses.get(i - 1).setType(StationUse.UseType.GONE_THROUGH);
                }
            }
        }
        originalPath = trip.toConnectionPath(network);
        partsDeleted = true;
        redrawPath();
    }

    private void saveChanges() {
        new SaveChangesTask().execute();
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
            Trip.persistConnectionPath(newPath, tripId);
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
