package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;

import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 7/9/17.
 */

@Entity(tableName = "station")
public class DbStation {
    @PrimaryKey
    private String id;

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
