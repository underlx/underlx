package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 4/6/17.
 */

public interface ILocator {
    public void setListener(OnStatusChangeListener listener);
    public Zone getLocation(Network network);
}
