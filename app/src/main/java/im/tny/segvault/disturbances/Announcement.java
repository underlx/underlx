package im.tny.segvault.disturbances;

import android.graphics.Color;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Gabriel on 30/09/2017.
 */

public class Announcement {
    private final String network;
    private final String title;
    private final String body;
    private final String url;
    private final String source;
    private final Date time;

    public Announcement(String network, String title, String body, String url, String source, Date time) {
        this.network = network;
        this.title = title;
        this.body = body;
        this.url = url;
        this.source = source;
        this.time = time;
    }

    public String getNetwork() {
        return network;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public Date getTime() {
        return time;
    }

    public static class Source {
        public final String id;
        public final int nameResourceId;
        public final int drawableResourceId;
        public final int color;

        public Source(String id, int nameResourceId, int drawableResourceId, int color) {
            this.id = id;
            this.nameResourceId = nameResourceId;
            this.drawableResourceId = drawableResourceId;
            this.color = color;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Source && id.equals(((Source)obj).id);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private final static List<Source> sources = Arrays.asList(
            new Source("pt-ml-rss", R.string.pref_notifs_announcement_source_pt_ml_rss, R.drawable.ic_web_accent_24dp, Color.parseColor("#F15D2A")),
            new Source("pt-ml-facebook", R.string.pref_notifs_announcement_source_pt_ml_facebook, R.drawable.ic_facebook_box_natural_24dp, Color.parseColor("#4267B2")));

    public static Collection<Source> getSources() {
        return Collections.unmodifiableList(sources);
    }

    public static Source getSource(String id) {
        for (Source s : sources) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
}
