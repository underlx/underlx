package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/5/17.
 */

public interface IInNetworkDetector {
    public void setListener(OnStatusChangeListener listener);
    public boolean inNetwork(Network network);
}
