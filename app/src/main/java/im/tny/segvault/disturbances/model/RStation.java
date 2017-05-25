package im.tny.segvault.disturbances.model;

import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

/**
 * Created by gabriel on 5/8/17.
 */

public class RStation extends RealmObject {
    @PrimaryKey
    private String id;

    @Required
    private String network;
    private boolean favorite;

    @Ignore
    private Station station;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public Station getStation() {
        return station;
    }

    public void setStop(Station station) {
        this.station = station;
        this.id = station.getId();
    }
}
