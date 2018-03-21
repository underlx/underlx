package im.tny.segvault.subway;

import java.util.List;

/**
 * Created by Gabriel on 21/03/2018.
 */

public class WorldPath {
    private String id;
    private List<float[]> path;

    public WorldPath(String id, List<float[]> path) {
        this.id = id;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public List<float[]> getPath() {
        return path;
    }
}
