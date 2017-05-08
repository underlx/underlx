package im.tny.segvault.disturbances.model;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

/**
 * Created by gabriel on 5/8/17.
 */

public class Trip extends RealmObject {
    @PrimaryKey
    private String id;

    private RealmList<StationUse> path;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RealmList<StationUse> getPath() {
        return path;
    }

    public void setPath(RealmList<StationUse> path) {
        this.path = path;
    }
}
