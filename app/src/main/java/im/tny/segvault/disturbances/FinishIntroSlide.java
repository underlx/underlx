package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.paolorotolo.appintro.ISlidePolicy;
import com.github.paolorotolo.appintro.ISlideSelectionListener;

import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

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

        title = (TextView) view.findViewById(R.id.title);
        image = (ImageView) view.findViewById(R.id.image);
        description = (TextView) view.findViewById(R.id.description);
        button = (Button) view.findViewById(R.id.button);

        refresh();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);

        return view;
    }

    private void refresh() {
        if (mListener == null || mListener.getMainService() == null) {
            return;
        }

        final MainService m = mListener.getMainService();

        if(m.getNetworks().size() == 0) {
            title.setText(R.string.intro_finish_wait_title);
            if(!Connectivity.isConnected(getContext())) {
                image.setImageResource(R.drawable.ic_frowning_intro);
                description.setText(R.string.intro_finish_connect_desc);
                button.setText(R.string.intro_finish_try_again);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        m.updateTopology();
                    }
                });
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
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onDonePressed(FinishIntroSlide.this);
                }
            });
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
                case MainService.ACTION_UPDATE_TOPOLOGY_PROGRESS:
                    lastKnownPercentage = intent.getIntExtra(MainService.EXTRA_UPDATE_TOPOLOGY_PROGRESS, 0);
                    refresh();
                    break;
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    lastKnownPercentage = 100;
                    refresh();
                    break;
            }
        }
    };
}