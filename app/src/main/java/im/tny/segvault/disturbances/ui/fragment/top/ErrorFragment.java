package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;

public class ErrorFragment extends TopFragment {
    private static final String ARG_TYPE = "type";
    private static final String ARG_ORIGINAL_ID = "original_id";
    private static final String ARG_ORIGINAL_ID_STRING = "original_id_string";

    public enum ErrorType {
        NO_TOPOLOGY, TOPOLOGY_DOWNLOADING, VERSION_TOO_OLD
    }

    private ErrorType errorType;
    private int originalFragmentId;
    private String originalFragmentIdString;

    private OnFragmentInteractionListener mListener;

    private TextView errorTitleView;
    private TextView errorMessageView;
    private Button retryButton;

    private View rootView;

    public ErrorFragment() {
        // Required empty public constructor
    }

    @Override
    public boolean needsTopology() {
        return false;
    }

    @Override
    public int getNavDrawerId() {
        return originalFragmentId;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return originalFragmentIdString;
    }

    public static ErrorFragment newInstance(ErrorType type, int originalFragmentId, String originalFragmentIdString) {
        ErrorFragment fragment = new ErrorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.toString());
        args.putInt(ARG_ORIGINAL_ID, originalFragmentId);
        args.putString(ARG_ORIGINAL_ID_STRING, originalFragmentIdString);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        errorType = ErrorType.valueOf(getArguments().getString(ARG_TYPE));
        originalFragmentId = getArguments().getInt(ARG_ORIGINAL_ID);
        originalFragmentIdString = getArguments().getString(ARG_ORIGINAL_ID_STRING);

        setUpActivity(getString(R.string.app_name), false, false);

        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_error, container, false);

        errorTitleView = rootView.findViewById(R.id.error_title_view);
        errorMessageView = rootView.findViewById(R.id.error_message_view);
        retryButton = rootView.findViewById(R.id.retry_button);

        switch (errorType) {
            case NO_TOPOLOGY:
                setUpMissingTopologyError();
                break;
            case TOPOLOGY_DOWNLOADING:
                setUpTopologyDownloadingError();
                break;
            case VERSION_TOO_OLD:
                setUpVersionTooOldError();
                break;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_CANCELLED);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        return rootView;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
    }

    private void setUpMissingTopologyError() {
        errorTitleView.setText(R.string.frag_error_missing_topology);
        errorMessageView.setText(getString(R.string.frag_error_missing_topology_desc));
        retryButton.setOnClickListener(view -> {
            Coordinator.get(getContext()).getMapManager().updateTopology();
            exitErrorScreen();
        });
    }

    private void setUpTopologyDownloadingError() {
        errorTitleView.setText(getString(R.string.frag_error_missing_topology));
        errorMessageView.setText(R.string.frag_error_topology_download_ongoing);
        retryButton.setVisibility(View.GONE);
    }

    private void setUpVersionTooOldError() {
        errorTitleView.setText(R.string.frag_error_version_too_old);
        errorMessageView.setText(R.string.frag_error_version_too_old_desc);
        retryButton.setText(R.string.frag_error_view_store_page);
        retryButton.setOnClickListener(view -> {
            final String appPackageName = getContext().getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });
    }

    private void exitErrorScreen() {
        if (mListener != null && isAdded()) {
            mListener.switchToPage(originalFragmentIdString, false);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MapManager.ACTION_UPDATE_TOPOLOGY_CANCELLED:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    if (errorType == ErrorType.NO_TOPOLOGY || errorType == ErrorType.TOPOLOGY_DOWNLOADING) {
                        exitErrorScreen();
                    }
                    break;
            }
        }
    };
}