package im.tny.segvault.disturbances;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.model.NotificationRule;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import io.realm.Realm;

import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_ANNOUNCEMENTS_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_DISTURBANCES_ID;

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

        SharedPreferences sharedPref = getSharedPreferences("notifsettings", MODE_PRIVATE);
        Set<String> linePref = sharedPref.getStringSet(PreferenceNames.NotifsLines, null);

        Coordinator coordinator = Coordinator.get(this);
        MapManager mapm = coordinator.getMapManager();
        Network snetwork = mapm.getNetwork(data.get("network"));
        if (snetwork == null) {
            return;
        }
        Line sline = snetwork.getLine(data.get("line"));
        if (sline == null) {
            return;
        }

        final boolean downtime = data.get("downtime").equals("true");
        if (downtime) {
            coordinator.getLineStatusCache().markLineAsDown(sline, new Date(remoteMessage.getSentTime()));
        } else {
            coordinator.getLineStatusCache().markLineAsUp(sline);
        }

        if (linePref != null && !linePref.contains(data.get("line"))) {
            // notifications disabled for this line
            return;
        }

        String status = API.Status.translateStatus(getApplicationContext(), data.get("status"), data.get("msgType"));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (!downtime && !sharedPref.getBoolean(PreferenceNames.NotifsServiceResumed, true)) {
            // notifications for normal service resumed disabled
            notificationManager.cancel(data.get("disturbance").hashCode());
            return;
        }

        Realm realm = Application.getDefaultRealmInstance(this);
        for (NotificationRule rule : realm.where(NotificationRule.class).findAll()) {
            if (rule.isEnabled() && rule.applies(new Date(remoteMessage.getSentTime()))) {
                realm.close();
                return;
            }
        }
        realm.close();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, "nav_disturbances");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, Math.abs(coordinator.getRandom().nextInt()), intent, PendingIntent.FLAG_UPDATE_CURRENT);

        String title = String.format(getString(R.string.notif_disturbance_title), Util.getLineNames(this, sline)[0]);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(title);
        bigTextStyle.bigText(status);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_DISTURBANCES_ID)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_disturbance_notif)
                .setColor(sline.getColor())
                .setContentTitle(title)
                .setContentText(status)
                .setAutoCancel(true)
                .setWhen(remoteMessage.getSentTime())
                .setSound(Uri.parse(sharedPref.getString(downtime ? PreferenceNames.NotifsRingtone : PreferenceNames.NotifsRegularizationRingtone, "content://settings/system/notification_sound")))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        if (sharedPref.getBoolean(downtime ? PreferenceNames.NotifsVibrate : PreferenceNames.NotifsRegularizationVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        notificationManager.notify(data.get("disturbance").hashCode(), notificationBuilder.build());
    }

    private void handleAnnouncementMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("network") || !data.containsKey("title") || !data.containsKey("body")
                || !data.containsKey("url") || !data.containsKey("source")) {
            return;
        }

        SharedPreferences sharedPref = getSharedPreferences("notifsettings", MODE_PRIVATE);
        Set<String> sourcePref = sharedPref.getStringSet(PreferenceNames.AnnouncementSources, null);

        Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
        notificationIntent.setData(Uri.parse(data.get("url")));
        PendingIntent contentIntent = PendingIntent.getActivity(this, Math.abs(Coordinator.get(this).getRandom().nextInt()), notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Announcement.Source source = Announcement.getSource(data.get("source"));
        if (source == null) {
            return;
        }

        String title = data.get("title");
        if (title.isEmpty()) {
            title = String.format(getString(R.string.notif_announcement_alt_title), getString(source.nameResourceId));
        }

        String body = data.get("body");
        if (body.isEmpty()) {
            body = getString(R.string.frag_announcement_no_text);
        }

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(title);
        bigTextStyle.bigText(body);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ANNOUNCEMENTS_ID)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_pt_ml_notif)
                .setColor(source.color)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setWhen(remoteMessage.getSentTime())
                .setSound(Uri.parse(sharedPref.getString(PreferenceNames.NotifsAnnouncementRingtone, "content://settings/system/notification_sound")))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);

        if (sharedPref.getBoolean(PreferenceNames.NotifsAnnouncementVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(data.get("url").hashCode(), notificationBuilder.build());
    }
}
