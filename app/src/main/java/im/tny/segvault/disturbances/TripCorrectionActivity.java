package im.tny.segvault.disturbances;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
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

    MainService locService;
    boolean locBound = false;

    private Network network;

    private StationPickerView startPicker;
    private StationPickerView endPicker;
    private LinearLayout pathLayout;
    private LinearLayout buttonsLayout;
    private Button saveButton;

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
        Object conn = getLastCustomNonConfigurationInstance();
        if (conn != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) conn;
            locService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            startService(new Intent(this, MainService.class));
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        setContentView(R.layout.activity_trip_correction);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (isStandalone) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        }

        startPicker = (StationPickerView) findViewById(R.id.start_picker);
        endPicker = (StationPickerView) findViewById(R.id.end_picker);
        pathLayout = (LinearLayout) findViewById(R.id.path_layout);
        buttonsLayout = (LinearLayout) findViewById(R.id.buttons_layout);
        saveButton = (Button) findViewById(R.id.save_button);

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(STATE_START_FOCUSED, false)) {
                startPicker.focusOnEntry();
            }
            if (savedInstanceState.getBoolean(STATE_END_FOCUSED, false)) {
                endPicker.focusOnEntry();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_correction, menu);
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

        StationPickerView.OnStationSelectedListener onStationSelectedListener = new StationPickerView.OnStationSelectedListener() {
            @Override
            public void onStationSelected(Station station) {
                redrawPath();
            }
        };
        startPicker.setOnStationSelectedListener(onStationSelectedListener);
        endPicker.setOnStationSelectedListener(onStationSelectedListener);

        StationPickerView.OnSelectionLostListener onSelectionLostListener = new StationPickerView.OnSelectionLostListener() {
            @Override
            public void onSelectionLost() {
                redrawPath();
            }
        };
        startPicker.setOnSelectionLostListener(onSelectionLostListener);
        endPicker.setOnSelectionLostListener(onSelectionLostListener);

        redrawPath();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveChanges();
            }
        });
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

        TripFragment.populatePathView(this, getLayoutInflater(), network, newPath, pathLayout, false);

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
                                stepview.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        removeVertex(idxOnTrip);
                                    }
                                });
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
        Trip.persistConnectionPath(newPath, tripId);
        finish();
    }

    private LocServiceConnection mConnection = new LocServiceConnection();

    class LocServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            locService = binder.getService();
            locBound = true;

            network = locService.getNetwork(networkId);

            populateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locBound = false;
        }

        public MainService.LocalBinder getBinder() {
            return binder;
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

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_TRIP_ID = "im.tny.segvault.disturbances.extra.TripCorrectionActivity.tripid";
    public static final String EXTRA_NETWORK_ID = "im.tny.segvault.disturbances.extra.TripCorrectionActivity.networkid";
    public static final String EXTRA_IS_STANDALONE = "im.tny.segvault.disturbances.extra.StationActivity.standalone";
}
