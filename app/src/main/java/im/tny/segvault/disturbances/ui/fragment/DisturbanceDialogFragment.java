package im.tny.segvault.disturbances.ui.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.API;
import im.tny.segvault.disturbances.Connectivity;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.adapter.DisturbanceRecyclerViewAdapter;
import im.tny.segvault.disturbances.ui.fragment.top.DisturbanceFragment;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 7/12/17.
 */
public class DisturbanceDialogFragment extends DialogFragment {
    private static final String ARG_DISTURBANCE_ID = "disturbance";

    public static DisturbanceDialogFragment newInstance(String disturbanceId) {
        DisturbanceDialogFragment fragment = new DisturbanceDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DISTURBANCE_ID, disturbanceId);
        fragment.setArguments(args);
        return fragment;
    }

    private String disturbanceId;
    private LinearLayout listContainer;
    private RecyclerView recyclerView;
    private TextView emptyView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        boolean isHtml = false;
        if (getArguments() != null) {
            disturbanceId = getArguments().getString(ARG_DISTURBANCE_ID);
        }
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.fragment_disturbance_list, null, false);

        // Set the adapter
        Context context = view.getContext();
        emptyView = view.findViewById(R.id.no_disturbances_view);
        recyclerView = view.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        listContainer = view.findViewById(R.id.list_container);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        new UpdateDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        builder.setPositiveButton(R.string.frag_disturbance_close, (dialogInterface, i) -> dialogInterface.dismiss());
        return builder.create();
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
        }
    }

    @Override
    public int show(FragmentTransaction transaction, String tag) {
        try {
            return super.show(transaction, tag);
        } catch (IllegalStateException e) {
            // ignore state loss
            return -1;
        }
    }

    @Override
    public void show(FragmentManager manager, String tag) {
        try {
            super.show(manager, tag);
        } catch (IllegalStateException e) {
            // ignore state loss
        }
    }

    private class UpdateDataTask extends AsyncTask<Void, Integer, Boolean> {
        private List<DisturbanceRecyclerViewAdapter.DisturbanceItem> items = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            emptyView.setText(R.string.status_loading);
            emptyView.setVisibility(View.VISIBLE);
        }

        protected Boolean doInBackground(Void... v) {
            Context context = getContext();
            if (getActivity() == null || context == null) {
                return false;
            }
            if (!Connectivity.isConnected(context)) {
                return false;
            }

            Collection<Network> networks = Coordinator.get(getContext()).getMapManager().getNetworks();
            try {
                API.Disturbance disturbance = API.getInstance().getDisturbance(disturbanceId);
                items.add(new DisturbanceRecyclerViewAdapter.DisturbanceItem(disturbance, networks, getContext()));
            } catch (APIException e) {
                return false;
            }
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            if (!isAdded()) {
                // prevent onPostExecute from doing anything if no longer attached to an activity
                return;
            }
            emptyView.setText(R.string.frag_disturbances_empty);
            if (result && recyclerView != null) {
                recyclerView.setAdapter(new DisturbanceRecyclerViewAdapter(items, null, false));
                recyclerView.invalidate();
                emptyView.setVisibility(View.GONE);
                listContainer.setVisibility(View.VISIBLE);
            } else {
                if(listContainer != null) {
                    listContainer.setVisibility(View.GONE);
                }
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }
}
