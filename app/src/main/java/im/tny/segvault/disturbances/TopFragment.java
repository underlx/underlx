package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by gabriel on 5/6/17.
 */

public abstract class TopFragment extends Fragment {
    private OnInteractionListener mListener;

    protected void setUpActivity(String title, int navDrawerId, boolean withFab, boolean withRefresh) {
        getActivity().setTitle(title);
        if (mListener != null) {
            mListener.checkNavigationDrawerItem(navDrawerId);
        }
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        if (withFab) {
            fab.show();
        } else {
            fab.hide();
        }
        fab.setOnClickListener(null);
        SwipeRefreshLayout srl = (SwipeRefreshLayout) getActivity().findViewById(R.id.swipe_container);
        srl.setEnabled(withRefresh);
        srl.setRefreshing(false);
        srl.setOnRefreshListener(null);
    }

    protected FloatingActionButton getFloatingActionButton() {
        return (FloatingActionButton) getActivity().findViewById(R.id.fab);
    }

    protected SwipeRefreshLayout getSwipeRefreshLayout() {
        return (SwipeRefreshLayout) getActivity().findViewById(R.id.swipe_container);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnInteractionListener) {
            mListener = (OnInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement TopFragment.OnInteractionListener");
        }
    }

    public interface OnInteractionListener {
        MainService getMainService();
        void checkNavigationDrawerItem(int id);
    }
}
