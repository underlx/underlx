package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/5/17.
 */

public interface IInNetworkDetector {
    public void initialize(Network network);
    public boolean inNetwork(Network network);
}
