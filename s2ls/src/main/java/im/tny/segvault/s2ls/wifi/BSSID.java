package im.tny.segvault.s2ls.wifi;

import java.io.Serializable;

/**
 * Created by gabriel on 4/6/17.
 */

public class BSSID implements Serializable {
    private final String bssid;

    public BSSID(String bssid) {
        this.bssid = bssid;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BSSID && ((BSSID)o).bssid.equals(this.bssid);
    }
}
