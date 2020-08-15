package im.tny.segvault.disturbances;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.NotificationRule;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_ANNOUNCEMENTS_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_DISTURBANCES_ID;

/**
 * Created by gabriel on 4/15/17.
 */

public class FCMService extends FirebaseMessagingService {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtil.updateResources(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocaleUtil.updateResources(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String from = remoteMessage.getFrom();
        switch (from) {
            case "/topics/broadcasts":
                handleBroadcastMessage(remoteMessage);
                break;
            case "/topics/broadcasts-debug":
                if (BuildConfig.DEBUG) {
                    handleBroadcastMessage(remoteMessage);
                }
                break;
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
                } else if (from.startsWith("/topics/pair-")) {
                    handlePersonalMessage(remoteMessage);
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

        String status = Util.enrichLineStatus(
                getApplicationContext(),
                snetwork.getId(),
                sline.getId(),
                data.get("status"),
                data.get("msgType"),
                new Date(remoteMessage.getSentTime()),
                null).toString();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (!downtime && !sharedPref.getBoolean(PreferenceNames.NotifsServiceResumed, true)) {
            // notifications for normal service resumed disabled
            notificationManager.cancel(data.get("disturbance").hashCode());
            return;
        }

        if (data.containsKey("official") &&
                data.get("official").equals("false") &&
                !sharedPref.getBoolean(PreferenceNames.NotifsCommunity, true)) {
            // notifications for community-reported disturbances disabled
            return;
        }

        AppDatabase db = Coordinator.get(this).getDB();
        for (NotificationRule rule : db.notificationRuleDao().getAll()) {
            if (rule.enabled && rule.applies(new Date(remoteMessage.getSentTime()))) {
                return;
            }
        }

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
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        if (sharedPref.getBoolean(downtime ? PreferenceNames.NotifsVibrate : PreferenceNames.NotifsRegularizationVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        if (notificationManager == null) {
            return;
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
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);

        if (sharedPref.getBoolean(PreferenceNames.NotifsAnnouncementVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(data.get("url").hashCode(), notificationBuilder.build());
    }

    private void handlePersonalMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("type")) {
            return;
        }

        if (!data.get("type").equals("posplay-notification")) {
            return;
        }

        String title = data.get("title");
        String body = data.get("body");

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(title);
        bigTextStyle.bigText(body);

        Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
        notificationIntent.setData(Uri.parse(data.get("url")));
        PendingIntent contentIntent = PendingIntent.getActivity(this, Math.abs(Coordinator.get(this).getRandom().nextInt()), notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ANNOUNCEMENTS_ID)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_posplay_blue_24dp)
                .setColor(Color.parseColor("#0078E7"))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setWhen(remoteMessage.getSentTime())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(remoteMessage.getMessageId().hashCode(), notificationBuilder.build());
    }

    private void handleBroadcastMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("id") || !data.containsKey("type")) {
            return;
        }

        if (data.containsKey("shardID") && !data.get("shardID").isEmpty() &&
                data.containsKey("shardMax") && !data.get("shardMax").isEmpty()) {
            int shardID = Util.tryParseInteger(data.get("shardID"), 1);
            int shardMax = Util.tryParseInteger(data.get("shardMax"), 1);

            String androidID = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            int thisShard = (Math.abs(androidID.hashCode()) % shardMax) + 1; // % gives values in range [0, shardMax[; by adding one we can use shards from e.g. 1/3 to 3/3 instead of 0/3 to 2/3, which is more human-friendly
            if (thisShard != shardID) {
                // we are not the chosen ones
                return;
            }
        }

        if (data.containsKey("versions") && !data.get("versions").isEmpty()) {
            String versions = data.get("versions");
            switch (versions.substring(0, 1)) {
                case "<": {
                    int v = Util.tryParseInteger(versions.substring(1), Integer.MAX_VALUE);
                    if (BuildConfig.VERSION_CODE >= v) {
                        return;
                    }
                    break;
                }
                case ">": {
                    int v = Util.tryParseInteger(versions.substring(1), Integer.MIN_VALUE);
                    if (BuildConfig.VERSION_CODE <= v) {
                        return;
                    }
                    break;
                }
                case "=": {
                    int v = Util.tryParseInteger(versions.substring(1), Integer.MIN_VALUE);
                    if (BuildConfig.VERSION_CODE != v) {
                        return;
                    }
                    break;
                }
                default:
                    // assume the filter is a list of versions separated by commas
                    String[] parts = versions.split(",");
                    boolean matchesOne = false;
                    for (String part : parts) {
                        if (Util.tryParseInteger(part, Integer.MIN_VALUE) == BuildConfig.VERSION_CODE) {
                            matchesOne = true;
                            break;
                        }
                    }
                    if (!matchesOne) {
                        return;
                    }
                    break;
            }
        }

        if (data.containsKey("locales") && !data.get("locales").isEmpty()) {
            LocaleUtil.updateResources(this);
            String currentLang = Util.getCurrentLanguage(this);
            String[] parts = data.get("locales").split(",");
            // negations are matched as a AND, non-negations as a OR
            // for example: en,pt,!es,!fr - (en OR pt) AND (NOT es AND NOT fr)
            // "undefined behavior" if e.g. en,!en appears
            boolean matchesOne = false;
            for (String part : parts) {
                boolean isNegation = part.startsWith("!");
                String lang = part.substring(isNegation ? 1 : 0);
                if (isNegation) {
                    if (currentLang.equals(lang)) {
                        // not for us
                        return;
                    }
                    matchesOne = true;
                } else if (currentLang.equals(lang)) {
                    // explicitly for us
                    matchesOne = true;
                    break;
                }
            }
            if (!matchesOne && parts.length > 0) {
                return;
            }
        }

        switch (data.get("type")) {
            case "notification":
                handleNotificationMessage(remoteMessage);
                break;
            case "command":
                handleCommandMessage(remoteMessage);
                break;
        }
    }

    // to be called by handleBroadcastMessage only!
    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("title") || !data.containsKey("body")) {
            return;
        }

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(data.get("title"));
        bigTextStyle.bigText(data.get("body"));

        Intent notificationIntent;
        if (data.containsKey("url") && !data.get("url").isEmpty()) {
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setData(Uri.parse(data.get("url")));
        } else {
            notificationIntent = new Intent(this, MainActivity.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(this, Math.abs(Coordinator.get(this).getRandom().nextInt()), notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ANNOUNCEMENTS_ID)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_logo_flat_24dp)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(data.get("title"))
                .setContentText(data.get("body"))
                .setAutoCancel(true)
                .setWhen(remoteMessage.getSentTime())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(data.get("id").hashCode(), notificationBuilder.build());
    }

    // to be called by handleBroadcastMessage only!
    private void handleCommandMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (!data.containsKey("command")) {
            return;
        }

        switch (data.get("command")) {
            case "update-topology":
                Coordinator.get(this).getMapManager().updateTopology();
                break;
            case "clear-notifs": {
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                if (notificationManager == null) {
                    return;
                }
                notificationManager.cancelAll();
                break;
            }
            case "clear-cache":
                Util.deleteCache(this);
                break;
            case "download-extras": {
                Collection<Network> networks = Coordinator.get(this).getMapManager().getNetworks();
                String[] arr = new String[networks.size()];
                int i = 0;
                for (Network n : networks) {
                    arr[i++] = n.getId();
                }
                Coordinator.get(this).cacheAllExtras(arr);
                break;
            }
        }
    }
}
