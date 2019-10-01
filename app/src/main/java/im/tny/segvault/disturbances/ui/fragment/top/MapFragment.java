package im.tny.segvault.disturbances.ui.fragment.top;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import im.tny.segvault.disturbances.BuildConfig;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.map.GoogleMapsMapStrategy;
import im.tny.segvault.disturbances.ui.map.MapStrategy;
import im.tny.segvault.disturbances.ui.map.WebViewMapStrategy;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.util.CustomFAB;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

import static android.content.Context.MODE_PRIVATE;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MapFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends TopFragment {
    private OnFragmentInteractionListener mListener;

    private static final String ARG_NETWORK_ID = "networkId";

    private LinearLayout filtersLayout;
    private FrameLayout container;
    private Button clearFiltersButton;
    private MenuItem filterItem;

    private String networkId;
    private Network network;

    private MapStrategy currentMapStrategy;

    private boolean mockLocationMode = false;

    private Bundle savedInstanceState;

    final List<List<String>> optionTagSets = new ArrayList<>();

    public MapFragment() {
        // Required empty public constructor
        optionTagSets.add(Arrays.asList("a_baby"));
        optionTagSets.add(Arrays.asList("a_store"));
        optionTagSets.add(Arrays.asList("a_wc"));
        optionTagSets.add(Arrays.asList("a_wifi"));
        optionTagSets.add(Arrays.asList("c_airport"));
        optionTagSets.add(Arrays.asList("c_bike"));
        optionTagSets.add(Arrays.asList("c_boat"));
        optionTagSets.add(Arrays.asList("c_bus"));
        optionTagSets.add(Arrays.asList("c_parking"));
        optionTagSets.add(Arrays.asList("c_taxi"));
        optionTagSets.add(Arrays.asList("c_train"));
        optionTagSets.add(Arrays.asList("m_escalator_platform"));
        optionTagSets.add(Arrays.asList("m_escalator_surface"));
        optionTagSets.add(Arrays.asList("m_lift_platform"));
        optionTagSets.add(Arrays.asList("m_lift_surface"));
        optionTagSets.add(Arrays.asList("m_platform"));
        optionTagSets.add(Arrays.asList("m_stepfree"));
        optionTagSets.add(Arrays.asList("s_lostfound"));
        optionTagSets.add(Arrays.asList("s_ticket1", "s_ticket2", "s_ticket3", "s_navegante"));
        optionTagSets.add(Arrays.asList("s_urgent_pass"));
        optionTagSets.add(Arrays.asList("s_info", "s_client"));
    }

    @Override
    public boolean needsTopology() {
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_map;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_map";
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance(String networkId) {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NETWORK_ID, networkId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            networkId = getArguments().getString(ARG_NETWORK_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_map_title), true, false);
        setHasOptionsMenu(true);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        this.container = view.findViewById(R.id.map_container);
        filtersLayout = view.findViewById(R.id.filters_layout);
        clearFiltersButton = view.findViewById(R.id.clear_filters_button);
        clearFiltersButton.setOnClickListener(view12 -> clearFilter());

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        this.savedInstanceState = savedInstanceState;

        CustomFAB fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_swap_horiz_white_24dp);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        params.setMargins(getResources().getDimensionPixelOffset(R.dimen.fab_margin),
                getResources().getDimensionPixelOffset(R.dimen.fab_margin),
                getResources().getDimensionPixelOffset(R.dimen.fab_margin),
                getResources().getDimensionPixelOffset(R.dimen.fab_margin));
        fab.setLayoutParams(params);
        fab.setOnClickListener(view1 -> {
            if (network != null) {
                switchMap(network.getMaps(), true);
            }
        });

        tryLoad(true);
        return view;
    }

    private void tryLoad(boolean initial) {
        if (network != null && !initial) {
            return;
        }
        network = Coordinator.get(getContext()).getMapManager().getNetwork(networkId);
        if (network == null) {
            return;
        }
        List<Network.Plan> maps = network.getMaps();
        switchMap(maps, false);
    }

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 10002;

    private void switchMap(List<Network.Plan> maps, boolean next) {
        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        int mapIndex = sharedPref.getInt(PreferenceNames.MapType, 0);
        if (next) {
            mapIndex++;
        }
        if (mapIndex > maps.size() - 1) {
            mapIndex = 0;
        }
        if (next) {
            SharedPreferences.Editor e = sharedPref.edit();
            e.putInt(PreferenceNames.MapType, mapIndex);
            e.apply();
        }

        Network.Plan selectedMap = maps.get(mapIndex);

        MapStrategy strategy = null;
        if (selectedMap instanceof Network.HtmlDiagram) {
            strategy = new WebViewMapStrategy(getContext(), networkId);
        } else if (selectedMap instanceof Network.WorldMap) {
            strategy = new GoogleMapsMapStrategy(getContext(), getLayoutInflater(), network,
                    () -> {
                        FragmentActivity activity = getActivity();
                        if (activity == null) {
                            return;
                        }
                        activity.runOnUiThread(() -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
                            }
                        });
                    },
                    action -> {
                        FragmentActivity act = getActivity();
                        if (act != null) {
                            act.runOnUiThread(action);
                        }
                    });
        }
        if (strategy == null) {
            return;
        }
        if (next && currentMapStrategy instanceof WebViewMapStrategy && strategy instanceof WebViewMapStrategy) {
            // faster map switching when switching between HtmlDiagrams
            ((WebViewMapStrategy) currentMapStrategy).switchMap((Network.HtmlDiagram) selectedMap);
        } else {
            if (currentMapStrategy != null) {
                currentMapStrategy.onPause();
                currentMapStrategy.onDestroy();
            }
            container.removeAllViews();
            currentMapStrategy = strategy;
            currentMapStrategy.setMockLocation(mockLocationMode);
            currentMapStrategy.initialize(container, selectedMap, savedInstanceState);
            currentMapStrategy.onResume();
        }
        if (filterItem != null) {
            filterItem.setVisible(strategy.isFilterable());
        }
    }

    public static class FilterDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final List<List<String>> selectedTagSets = new ArrayList<>();
            final List<List<String>> optionTagSets = new ArrayList<>();
            MapFragment frag = (MapFragment) getTargetFragment();
            if (frag != null) {
                optionTagSets.addAll(frag.optionTagSets);
                selectedTagSets.addAll(frag.currentTagSets);
            }
            List<CharSequence> optionTexts = new ArrayList<>();
            for (List<String> tagSet : optionTagSets) {
                optionTexts.add(Util.getStringForStationTag(getContext(), tagSet.get(0)));
            }

            boolean[] checkedItems = new boolean[optionTexts.size()];
            for (int i = 0; i < optionTagSets.size(); i++) {
                checkedItems[i] = selectedTagSets.contains(optionTagSets.get(i));
            }

            CharSequence[] optionTextsArray = new CharSequence[optionTexts.size()];
            optionTexts.toArray(optionTextsArray);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.frag_map_filter_title)
                    .setMultiChoiceItems(optionTextsArray, checkedItems,
                            (dialog, which, isChecked) -> {
                                if (isChecked) {
                                    selectedTagSets.add(optionTagSets.get(which));
                                } else {
                                    selectedTagSets.remove(optionTagSets.get(which));
                                }
                                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selectedTagSets.size() > 0);
                                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(selectedTagSets.size() > 0);
                            })
                    // Set the action buttons
                    .setPositiveButton(R.string.frag_map_filter_intersection, (dialog, id) -> {
                        MapFragment frag12 = (MapFragment) getTargetFragment();
                        if (frag12 != null) {
                            frag12.applyFilter(selectedTagSets, MapFilterType.INTERSECTION);
                        }
                    })
                    .setNegativeButton(R.string.frag_map_filter_union, (dialog, id) -> {
                        MapFragment frag1 = (MapFragment) getTargetFragment();
                        if (frag1 != null) {
                            frag1.applyFilter(selectedTagSets, MapFilterType.UNION);
                        }
                    });

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialog1 -> {
                Context context = getContext();
                if (context == null) {
                    return;
                }
                DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
                // limit listview height to 1/2 of the screen height to avoid buttons going in a scrollview
                ListView lv = ((AlertDialog) dialog1).getListView();
                ViewGroup.LayoutParams lp = lv.getLayoutParams();
                lp.height = displayMetrics.heightPixels / 2;
                lv.requestLayout();
                ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selectedTagSets.size() > 0);
                ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(selectedTagSets.size() > 0);
            });
            return dialog;
        }
    }

    private List<List<String>> currentTagSets = new ArrayList<>();

    private void applyFilter(List<List<String>> tagSets, MapFilterType type) {
        List<Station> stationsToShow = new ArrayList<>();
        for (Station station : network.getStations()) {
            switch (type) {
                case UNION:
                    for (List<String> tagSet : tagSets) {
                        if (!Collections.disjoint(station.getAllTags(), tagSet)) {
                            stationsToShow.add(station);
                            break;
                        }
                    }
                    break;
                case INTERSECTION: {
                    boolean hasAll = true;
                    for (List<String> tagSet : tagSets) {
                        if (Collections.disjoint(station.getAllTags(), tagSet)) {
                            hasAll = false;
                            break;
                        }
                    }
                    if (hasAll) {
                        stationsToShow.add(station);
                    }
                }
                break;
            }
        }

        if (stationsToShow.size() == 0) {
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.frag_map_filter_no_match)
                    .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> dialog.dismiss()).show();
            return;
        }

        filtersLayout.setVisibility(View.VISIBLE);
        currentTagSets.clear();
        currentTagSets.addAll(tagSets);
        currentMapStrategy.setFilteredStations(stationsToShow);
    }

    private void clearFilter() {
        filtersLayout.setVisibility(View.GONE);
        currentTagSets.clear();
        currentMapStrategy.clearFilter();
    }

    public enum MapFilterType {UNION, INTERSECTION}


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.map, menu);

        SharedPreferences sharedPref = getContext().getSharedPreferences("settings", MODE_PRIVATE);
        if (sharedPref != null) {
            if (BuildConfig.DEBUG && sharedPref.getBoolean(PreferenceNames.DeveloperMode, false)) {
                menu.findItem(R.id.menu_mock_location).setVisible(true);
            }
        }

        filterItem = menu.findItem(R.id.menu_filter);
        if (currentMapStrategy != null) {
            filterItem.setVisible(currentMapStrategy.isFilterable());
        }

        new Handler().post(() -> showTargetPrompt());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (currentMapStrategy == null) {
            // not yet ready
            return super.onOptionsItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.menu_filter: {
                FilterDialogFragment dialogFragment = new FilterDialogFragment();
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), "filterdialog");
                break;
            }
            case R.id.menu_zoom_out:
                currentMapStrategy.zoomOut();
                return true;
            case R.id.menu_zoom_in:
                currentMapStrategy.zoomIn();
                return true;
            case R.id.menu_mock_location:
                mockLocationMode = !item.isChecked();
                item.setChecked(mockLocationMode);
                if (currentMapStrategy != null) {
                    currentMapStrategy.setMockLocation(mockLocationMode);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentMapStrategy != null) {
            currentMapStrategy.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentMapStrategy != null) {
            currentMapStrategy.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentMapStrategy != null) {
            currentMapStrategy.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (currentMapStrategy != null) {
            currentMapStrategy.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentMapStrategy != null) {
            currentMapStrategy.onSaveInstanceState(outState);
        }
    }

    private void showTargetPrompt() {
        Context context = getContext();
        boolean isFirstOpen = false;
        if (context != null) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
            if (sharedPref != null) {
                isFirstOpen = sharedPref.getBoolean("fuse_first_map_open", true);
            }
        }

        if (!isFirstOpen) {
            return;
        }

        new MaterialTapTargetPrompt.Builder(getActivity())
                .setTarget(R.id.fab)
                .setPrimaryText(R.string.frag_map_switch_type_taptarget_title)
                .setSecondaryText(R.string.frag_map_switch_type_taptarget_subtitle)
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        // User has pressed the prompt target
                        Context context1 = getContext();
                        if (context1 != null) {
                            SharedPreferences sharedPref = context1.getSharedPreferences("settings", MODE_PRIVATE);
                            SharedPreferences.Editor e = sharedPref.edit();
                            e.putBoolean("fuse_first_map_open", false);
                            e.apply();
                        }
                    }
                })
                .setFocalColour(Color.TRANSPARENT)
                .setBackgroundColour(ContextCompat.getColor(getContext(), R.color.colorPrimaryLight))
                .show();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    tryLoad(false);
                    break;
            }
        }
    };
}
