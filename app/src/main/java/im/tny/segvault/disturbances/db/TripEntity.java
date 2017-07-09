package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.Entity;

import io.realm.annotations.PrimaryKey;

/**
 * Created by gabriel on 7/9/17.
 */

@Entity(tableName = "trip")
class TripEntity {
    @PrimaryKey
    private String id;

    private boolean synced;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
