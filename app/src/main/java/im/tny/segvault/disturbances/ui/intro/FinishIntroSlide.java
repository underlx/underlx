package im.tny.segvault.disturbances.ui.intro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.paolorotolo.appintro.ISlidePolicy;
import com.github.paolorotolo.appintro.ISlideSelectionListener;

import im.tny.segvault.disturbances.Connectivity;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.R;

/**
 * Created by Gabriel on 27/07/2017.
 */

public class FinishIntroSlide extends Fragment implements ISlideSelectionListener, ISlidePolicy {
    private OnFragmentInteractionListener mListener;
    private View view;
    private TextView title;
    private ImageView image;
    private TextView description;
    private Button button;

    private int lastKnownPercentage = 0;
    private boolean canLeave = false;

    public static FinishIntroSlide newInstance() {
        FinishIntroSlide sampleSlide = new FinishIntroSlide();
        return sampleSlide;
    }

    public FinishIntroSlide() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_intro_finish, container, false);

        title = view.findViewById(R.id.title);
        image = view.findViewById(R.id.image);
        description = view.findViewById(R.id.description);
        button = view.findViewById(R.id.button);

        refresh();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        return view;
    }

    private void refresh() {
        if (mListener == null || mListener.getMainService() == null) {
            return;
        }

        final MapManager m = Coordinator.get(getContext()).getMapManager();

        if(m.getNetworks().size() == 0) {
            title.setText(R.string.intro_finish_wait_title);
            if(!Connectivity.isConnected(getContext())) {
                image.setImageResource(R.drawable.ic_frowning_intro);
                description.setText(R.string.intro_finish_connect_desc);
                button.setText(R.string.intro_finish_try_again);
                button.setOnClickListener(v -> m.updateTopology());
                button.setVisibility(View.VISIBLE);
            } else {
                image.setImageResource(R.drawable.ic_hourglass_flowing_sand_intro);
                description.setText(String.format(getString(R.string.intro_finish_downloading), lastKnownPercentage));
                button.setVisibility(View.GONE);
            }
            canLeave = false;
        } else {
            title.setText(R.string.intro_finish_done_title);
            image.setImageResource(R.drawable.ic_ok_hand_intro);
            description.setText(R.string.intro_finish_done_desc);
            button.setText(R.string.intro_finish_get_started);
            button.setOnClickListener(v -> mListener.onDonePressed(FinishIntroSlide.this));
            button.setVisibility(View.VISIBLE);
            canLeave = true;
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

    @Override
    public void onSlideSelected() {
        refresh();
    }

    @Override
    public void onSlideDeselected() {

    }

    @Override
    public boolean isPolicyRespected() {
        return canLeave;
    }

    @Override
    public void onUserIllegallyRequestedNextPage() {

    }

    public interface OnFragmentInteractionListener {
        MainService getMainService();
        void onDonePressed(Fragment fragment);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MapManager.ACTION_UPDATE_TOPOLOGY_PROGRESS:
                    lastKnownPercentage = intent.getIntExtra(MapManager.EXTRA_UPDATE_TOPOLOGY_PROGRESS, 0);
                    refresh();
                    break;
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    lastKnownPercentage = 100;
                    refresh();
                    break;
            }
        }
    };
}