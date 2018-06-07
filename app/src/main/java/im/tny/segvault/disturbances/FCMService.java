package im.tny.segvault.disturbances;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by gabriel on 4/15/17.
 */

public class FCMService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String from = remoteMessage.getFrom();
        switch (from) {
            case "/topics/disturbances":
                handleDisturbanceMessage(remoteMessage);
                break;
            case "/topics/disturbances-debug":
                if (BuildConfig.DEBUG) {
                    handleDisturbanceMessage(remoteMessage);
                }
                break;
            default:
                if (from.startsWith("/topics/announcements-")) {
                    handleAnnouncementMessage(remoteMessage);
                } else if (BuildConfig.DEBUG && from.startsWith("/topics/announcements-debug-")) {
                    handleAnnouncementMessage(remoteMessage);
                }
                break;
        }
    }

    private void handleDisturbanceMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("network") || !data.containsKey("line") || !data.containsKey("disturbance")
                || !data.containsKey("status") || !data.containsKey("downtime") || !data.containsKey("msgType")) {
            return;
        }

        if (new Date().getTime() - remoteMessage.getSentTime() > TimeUnit.HOURS.toMillis(5)) {
            // discard messages that have been sent more than 5 hours ago
            return;
        }

        MainService.startForDisturbanceNotification(getApplicationContext(),
                data.get("network"), data.get("line"), data.get("disturbance"), data.get("status"),
                data.get("msgType"), data.get("downtime").equals("true"), remoteMessage.getSentTime());
    }

    private void handleAnnouncementMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("network") || !data.containsKey("title") || !data.containsKey("body")
                || !data.containsKey("url") || !data.containsKey("source")) {
            return;
        }

        MainService.startForAnnouncementNotification(getApplicationContext(),
                data.get("network"), data.get("title"), data.get("body"),
                data.get("url"), data.get("source"), remoteMessage.getSentTime());
    }
}
