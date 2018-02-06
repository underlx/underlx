package im.tny.segvault.subway;

import java.util.Map;

/**
 * Created by Gabriel on 06/02/2018.
 */

public class POI {
    private String id;
    private String type;
    private float[] worldCoord;
    private String webURL;
    private String mainLocale;
    private Map<String, String> names;

    public POI(String id, String type, float[] worldCoord, String webURL, String mainLocale, Map<String, String> names) {
        this.id = id;
        this.type = type;
        this.worldCoord = worldCoord;
        this.webURL = webURL;
        this.mainLocale = mainLocale;
        this.names = names;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public float[] getWorldCoord() {
        return worldCoord;
    }

    public String getWebURL() {
        return webURL;
    }

    public String[] getNames(String locale) {
        if (locale.equals(mainLocale)) {
            return new String[]{names.get(locale)};
        }
        if (names.get(locale) != null) {
            return new String[]{names.get(mainLocale), names.get(locale)};
        }
        // no translation available for this locale
        return new String[]{names.get(mainLocale)};
    }
}
