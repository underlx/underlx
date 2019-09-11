package im.tny.segvault.disturbances;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

import static android.content.Context.MODE_PRIVATE;

public class MqttManager {
    private Context context;
    private final Object mqttLock = new Object();
    private Mqtt3BlockingClient mqttClient;
    private Map<Integer, HashSet<String>> mqttSubscriptionsByParty = new HashMap<>();
    private Map<String, HashSet<Integer>> mqttSubscriptionsByTopic = new HashMap<>();
    private Set<Integer> parties = new HashSet<>();
    private final static int SPECIAL_RECONNECT_PARTY_ID = -999;
    private final static int INITIAL_RECONNECT_DELAY_S = 5;
    private final static int MAX_RECONNECT_DELAY_S = 60;
    private final static int KEEPALIVE_S = 29;
    private int reconnectAttempts = 0;

    public MqttManager(Context context) {
        this.context = context;
    }

    private static class DisconnectedListener implements MqttClientDisconnectedListener {
        private WeakReference<MqttManager> parentRef;

        DisconnectedListener(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        @Override
        public void onDisconnected(MqttClientDisconnectedContext context) {
            Log.d("MQTT", "onDisconnected");
            if (context.getSource() == MqttDisconnectSource.USER) {
                Log.d("MQTT", "Disconnected on request, not reconnecting");
                return;
            }

            MqttManager parent = parentRef.get();
            if (parent == null) {
                return;
            }

            new MQTTReconnectTask(parent).execute();
        }
    }

    private static class MQTTReconnectTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<MqttManager> parentRef;
        private HashSet<String> topics;

        MQTTReconnectTask(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            long sleepTime;
            synchronized (parent.mqttLock) {
                sleepTime = ++parent.reconnectAttempts * INITIAL_RECONNECT_DELAY_S * 1000;
                if (sleepTime > MAX_RECONNECT_DELAY_S * 1000) {
                    sleepTime = MAX_RECONNECT_DELAY_S * 1000;
                }

                if (parent.reconnectAttempts > 20) {
                    return false;
                }
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            topics = new HashSet<>();
            synchronized (parent.mqttLock) {
                for (HashSet<String> s : parent.mqttSubscriptionsByParty.values()) {
                    topics.addAll(s);
                }
            }

            if (topics.size() == 0) {
                Log.d("MQTT", "No topics registered now, not reconnecting");
                return false;
            }

            MqttClient client;
            synchronized (parent.mqttLock) {
                client = parent.mqttClient;
            }
            if (client != null && client.getState().isConnected()) {
                Log.d("MQTT", "Client already connected, not reconnecting");
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (aBoolean != null && aBoolean) {
                MqttManager parent = parentRef.get();
                if (parent == null) {
                    return;
                }
                Log.d("MQTT", "Reconnecting");
                String[] topicsArr = new String[topics.size()];
                new MQTTConnectTask(parent, SPECIAL_RECONNECT_PARTY_ID).execute(topics.toArray(topicsArr));
            }
        }
    }

    private static class MQTTConnectTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<MqttManager> parentRef;
        private String[] initialTopics;
        private int clientID;

        MQTTConnectTask(MqttManager parent, int clientID) {
            parentRef = new WeakReference<>(parent);
            this.clientID = clientID;
        }

        @Override
        protected Boolean doInBackground(String... initialTopics) {
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                // HiveMQ MQTT Client crashes on Android < 4.4 due to the use of StandardCharsets
                // Could probably be fixed by forking/adding backports like android-retrofix, but it's not worth it
                // (at the time of writing, the app has less than 10 users on versions below 4.4)
                return false;
            }
            this.initialTopics = initialTopics;
            if (!Coordinator.get(parent.context).getPairManager().isPaired() ||
                    !Coordinator.get(parent.context).getPairManager().isActivated()) {
                return false;
            }

            API.MQTTConnectionInfo info = API.getInstance().getMQTTConnectionInfo();

            if (info == null) {
                return false;
            }

            synchronized (parent.mqttLock) {
                if (parent.mqttClient != null && clientID != SPECIAL_RECONNECT_PARTY_ID) {
                    return false;
                }
            }

            Mqtt3ClientBuilder clientBuilder = MqttClient.builder()
                    .identifier(String.format("%s-%d", Coordinator.get(parent.context).getPairManager().getPairKey(), new Date().getTime()))
                    .serverHost(info.host)
                    .serverPort(info.port)
                    .addDisconnectedListener(new DisconnectedListener(parent))
                    .useMqttVersion3();
            if (info.isTLS) {
                clientBuilder = clientBuilder.sslWithDefaultConfig();
            }
            Mqtt3BlockingClient client = clientBuilder.build().toBlocking();

            try {
                client.connectWith()
                        .keepAlive(KEEPALIVE_S)
                        .cleanSession(true) // we know how to re-subscribe to the topics we want
                        .simpleAuth()
                        .username(Coordinator.get(parent.context).getPairManager().getPairKey())
                        .password(Coordinator.get(parent.context).getPairManager().getPairSecret().getBytes("UTF-8"))
                        .applySimpleAuth().send();
            } catch (Exception e) {
                // oh well
                return false;
            }

            synchronized (parent.mqttLock) {
                parent.mqttClient = client;
                parent.reconnectAttempts = 0;
            }
            Log.d("MQTT", "connected");
            new Thread(new ConsumeRunnable(parent)).start();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            MqttManager parent = parentRef.get();
            if (parent == null || !success) {
                return;
            }
            new MQTTSubscribeTask(parent, clientID).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, initialTopics);
        }
    }

    private static class MQTTDisconnectTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<MqttManager> parentRef;

        MQTTDisconnectTask(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return null;
            }

            Mqtt3BlockingClient client;
            synchronized (parent.mqttLock) {
                if (parent.mqttClient == null) {
                    return null;
                }
                client = parent.mqttClient;

                parent.mqttSubscriptionsByTopic.clear();
                parent.mqttSubscriptionsByParty.clear();

                // setting this to null tells the message consumption thread to stop
                parent.mqttClient = null;
            }

            try {
                client.disconnect();
            } catch (Exception e) {
                // oh well
                e.printStackTrace();
            }
            Log.d("MQTT", "disconnected");
            return null;
        }
    }

    private static class MQTTSubscribeTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private int partyID;

        MQTTSubscribeTask(MqttManager parent, int partyID) {
            parentRef = new WeakReference<>(parent);
            this.partyID = partyID;
        }

        @Override
        protected Void doInBackground(String... topics) {
            MqttManager parent = parentRef.get();
            if (parent == null || topics == null || topics.length == 0) {
                return null;
            }
            Mqtt3BlockingClient client;
            synchronized (parent.mqttLock) {
                if (parent.mqttClient == null) {
                    return null;
                }
                client = parent.mqttClient;
            }

            try {
                for (String topic : topics) {
                    client.subscribeWith().topicFilter(topic).qos(MqttQos.AT_MOST_ONCE).send();
                }
            } catch (Exception e) {
                // oh well
                e.printStackTrace();
                return null;
            }

            if (partyID != SPECIAL_RECONNECT_PARTY_ID) {
                synchronized (parent.mqttLock) {
                    HashSet<String> byParty = parent.mqttSubscriptionsByParty.get(partyID);
                    if (byParty == null) {
                        byParty = new HashSet<>();
                        parent.mqttSubscriptionsByParty.put(partyID, byParty);
                    }
                    for (String topic : topics) {
                        byParty.add(topic);
                        HashSet<Integer> byTopic = parent.mqttSubscriptionsByTopic.get(topic);
                        if (byTopic == null) {
                            byTopic = new HashSet<>();
                            parent.mqttSubscriptionsByTopic.put(topic, byTopic);
                        }
                        byTopic.add(partyID);
                        Log.d("MQTT", "Party " + partyID + " subscribed to topic " + topic);
                    }
                }
            } else {
                for (String topic : topics) {
                    Log.d("MQTT", "Reconnect resubscribed to topic " + topic);
                }
            }
            return null;

        }
    }

    private static class MQTTUnsubscribeTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private int partyID;

        MQTTUnsubscribeTask(MqttManager parent, int partyID) {
            parentRef = new WeakReference<>(parent);
            this.partyID = partyID;
        }

        @Override
        protected Void doInBackground(String... topics) {
            MqttManager parent = parentRef.get();
            if (parent == null || topics == null || topics.length == 0) {
                return null;
            }
            List<String> actualTopics = new ArrayList<>();
            Mqtt3BlockingClient client;
            synchronized (parent.mqttLock) {
                client = parent.mqttClient;
                if (parent.mqttClient == null) {
                    return null;
                }

                HashSet<String> byParty = parent.mqttSubscriptionsByParty.get(partyID);
                if (byParty == null) {
                    byParty = new HashSet<>();
                }
                for (String topic : topics) {
                    HashSet<Integer> byTopic = parent.mqttSubscriptionsByTopic.get(topic);
                    if (byTopic == null || byTopic.size() <= 1) {
                        // this is the only party interested in this topic, actually unsubscribe
                        actualTopics.add(topic);
                    } else {
                        // some parties are still interested in this topic, don't actually unsubscribe but register the lack of interest of this party
                        Log.d("MQTT", "Party " + partyID + " unsubscribed from topic " + topic);
                        byTopic.remove(partyID);
                        byParty.remove(topic);
                    }
                }
            }

            if (actualTopics.size() > 0) {
                try {
                    for (String topic : actualTopics) {
                        client.unsubscribeWith().topicFilter(topic).send();
                    }
                } catch (Exception e) {
                    // oh well
                    e.printStackTrace();
                }
            }

            synchronized (parent.mqttLock) {
                HashSet<String> byParty = parent.mqttSubscriptionsByParty.get(partyID);
                if (byParty == null) {
                    byParty = new HashSet<>();
                }
                for (String topic : actualTopics) {
                    Log.d("MQTT", "Party " + partyID + " unsubscribed from topic " + topic);
                    HashSet<Integer> byTopic = parent.mqttSubscriptionsByTopic.get(topic);
                    if (byTopic != null) {
                        byTopic.remove(partyID);
                        if (byTopic.size() == 0) {
                            parent.mqttSubscriptionsByTopic.remove(topic);
                        }
                    }
                    byParty.remove(topic);
                }
                if (byParty.size() == 0) {
                    parent.mqttSubscriptionsByParty.remove(partyID);
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            MqttManager parent = parentRef.get();
            if (parent == null) {
                return;
            }
            synchronized (parent.mqttLock) {
                if (parent.parties.size() == 0) {
                    new MQTTDisconnectTask(parent).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    private static class MQTTPublishTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private String topic;
        private byte[] payload;

        MQTTPublishTask(MqttManager parent, String topic, byte[] payload) {
            parentRef = new WeakReference<>(parent);
            this.topic = topic;
            this.payload = payload;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return null;
            }

            Mqtt3BlockingClient client;
            synchronized (parent.mqttLock) {
                client = parent.mqttClient;
            }

            try {
                client.publishWith().topic(topic).payload(payload).qos(MqttQos.AT_MOST_ONCE).send();
            } catch (Exception e) {
                // oh well
                e.printStackTrace();
            }
            return null;
        }
    }

    private static class ConsumeRunnable implements Runnable {
        private WeakReference<MqttManager> parentRef;

        ConsumeRunnable(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        @SuppressLint("NewAPI")
        public void run() {
            while (true) {
                MqttManager parent = parentRef.get();
                if (parent == null) {
                    return;
                }

                Mqtt3BlockingClient client;
                synchronized (parent.mqttLock) {
                    client = parent.mqttClient;
                }
                if (client == null) {
                    return;
                }
                try {
                    MapManager mapManager = Coordinator.get(parent.context).getMapManager();

                    Mqtt3Publish publish;
                    try (Mqtt3BlockingClient.Mqtt3Publishes publishes = client.publishes(MqttGlobalPublishFilter.ALL)) {
                        Optional<Mqtt3Publish> publishMessage = publishes.receive(10, TimeUnit.SECONDS);

                        if (!publishMessage.isPresent()) {
                            continue;
                        }
                        publish = publishMessage.get();
                    }
                    String topicName = publish.getTopic().toString();
                    Log.d("MQTT", "Received message on topic " + topicName);

                    if (topicName.startsWith("msgpack/vehicleeta/") || topicName.startsWith("dev-msgpack/vehicleeta/")) {
                        // should be of the form msgpack/vehicleeta/pt-ml/pt-ml-av
                        String[] parts = topicName.split("/");
                        if (parts.length != 4) {
                            continue;
                        }
                        Network network = mapManager.getNetwork(parts[2]);
                        if (network == null) {
                            continue;
                        }
                        Station station = network.getStation(parts[3]);
                        if (station == null) {
                            continue;
                        }
                        parent.processVehicleETAMessage(station, publish.getPayloadAsBytes());
                        Intent intent = new Intent(ACTION_VEHICLE_ETAS_UPDATED);
                        intent.putExtra(EXTRA_VEHICLE_ETAS_NETWORK, network.getId());
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(parent.context);
                        bm.sendBroadcast(intent);
                    }
                } catch (Exception e) {
                    if (!client.getState().isConnected()) {
                        Log.d("MQTT", "ConsumeRunnable stopping as client disconnected");
                        return;
                    }
                    e.printStackTrace();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

            }
        }
    }


    private int currPartyID = 0;

    public int connect(String... initialTopics) {
        synchronized (mqttLock) {
            int id = currPartyID++;
            if (parties.size() == 0) {
                new MQTTConnectTask(this, id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, initialTopics);
            } else {
                Log.d("MQTT", "will subscribe");
                subscribe(id, initialTopics);
            }
            parties.add(id);
            return id;
        }
    }

    public void disconnect(int partyID) {
        synchronized (mqttLock) {
            if (parties.size() == 0 || (parties.size() == 1 && parties.contains(partyID))) {
                Log.d("MQTT", "will disconnect");
                new MQTTDisconnectTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                HashSet<String> byParty = mqttSubscriptionsByParty.get(partyID);
                if (byParty != null && byParty.size() > 0) {
                    String arr[] = new String[byParty.size()];
                    new MQTTUnsubscribeTask(this, partyID).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, byParty.toArray(arr));
                }
            }
            parties.remove(partyID);
        }
    }

    public void disconnectAll() {
        synchronized (mqttLock) {
            new MQTTDisconnectTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            parties.clear();
        }
    }

    public void subscribe(int partyID, String... topics) {
        new MQTTSubscribeTask(this, partyID).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, topics);
    }

    public void unsubscribe(int partyID, String... topics) {
        new MQTTUnsubscribeTask(this, partyID).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, topics);
    }

    public void unsubscribeAll(int partyID) {
        synchronized (mqttLock) {
            Set<String> subs = mqttSubscriptionsByParty.get(partyID);
            if (subs.size() == 0) {
                return;
            }
            String arr[] = new String[subs.size()];
            new MQTTUnsubscribeTask(this, partyID).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, subs.toArray(arr));
        }
    }

    public void publish(int partyID, String topic, byte[] payload) {
        new MQTTPublishTask(this, topic, payload).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private final Map<String, VehicleETAValue> vehicleETAs = new HashMap<>();
    private final Object vehicleETAsLock = new Object();

    private String getVehicleETAMapKey(Network network, Station station, Station direction) {
        return String.format("%s#%s#%s", network.getId(), station.getId(), direction.getId());
    }

    private static class VehicleETAValue {
        public API.MQTTvehicleETA eta;
        public Date storedAt;
    }

    private void storeVehicleETA(Station station, API.MQTTvehicleETA eta) {
        Station direction = station.getNetwork().getStation(eta.direction);
        if (direction == null) {
            return;
        }

        String key = getVehicleETAMapKey(station.getNetwork(), station, direction);
        VehicleETAValue value = new VehicleETAValue();
        value.eta = eta;
        value.storedAt = new Date();
        synchronized (vehicleETAsLock) {
            API.MQTTvehicleETA existingETA = getVehicleETA(station, direction);
            // use the made field (unix timestamp coming from server, so it's not synced to the local clock) as kind of a lamport clock
            // this rejects the ETA if we already have a more recent one (protection against out of order messages)
            if (existingETA != null && existingETA.made > eta.made) {
                return;
            }
            vehicleETAs.put(key, value);
        }
    }

    public API.MQTTvehicleETA getVehicleETA(Station station, Station direction) {
        String key = getVehicleETAMapKey(station.getNetwork(), station, direction);
        synchronized (vehicleETAsLock) {
            if (!vehicleETAs.containsKey(key)) {
                return null;
            }
            VehicleETAValue value = vehicleETAs.get(key);
            if (new Date().getTime() - value.storedAt.getTime() > TimeUnit.SECONDS.toMillis(value.eta.validFor)) {
                return null;
            }
            return value.eta;
        }
    }

    // returns a map where the key is the direction ID
    public Map<String, API.MQTTvehicleETA> getVehicleETAsForStation(Station station) {
        Map<String, API.MQTTvehicleETA> result = new HashMap<>();
        synchronized (vehicleETAsLock) {
            for (String key : vehicleETAs.keySet()) {
                String[] parts = key.split("#");
                if (parts[0].equals(station.getNetwork().getId()) && parts[1].equals(station.getId())) {
                    VehicleETAValue value = vehicleETAs.get(key);
                    if (new Date().getTime() - value.storedAt.getTime() > TimeUnit.SECONDS.toMillis(value.eta.validFor)) {
                        // do not include expired ones
                        continue;
                    }
                    result.put(parts[2], value.eta);
                }
            }
        }
        return result;
    }

    private void processVehicleETAMessage(Station station, byte[] payload) {
        ObjectMapper mapper = API.getInstance().getMapper();

        try {
            List<API.MQTTvehicleETA> etas = mapper.readValue(payload, new TypeReference<List<API.MQTTvehicleETA>>() {
            });
            for (API.MQTTvehicleETA eta : etas) {
                Log.d("MQTT", "Deserialized MQTTvehicleETA Class: " + eta.getClass().getSimpleName());
                storeVehicleETA(station, eta);
            }
        } catch (IOException e) {
            // oh well
            e.printStackTrace();
        }
    }

    public String getVehicleETAsTopicForStation(Station station) {
        return getVehicleETAsTopicForStation(station.getNetwork().getId(), station.getId());
    }

    public String getVehicleETAsTopicForStation(String networkId, String stationId) {
        return String.format("%s%s/%s", getVehicleETAsTopicPrefix(), networkId, stationId);
    }

    public String getVehicleETAsTopicPrefix() {
        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean devMode = sharedPref.getBoolean(PreferenceNames.DeveloperMode, false);

        if (devMode) {
            return "dev-msgpack/vehicleeta/";
        } else {
            return "msgpack/vehicleeta/";
        }
    }

    public String getLocationPublishTopicForNetwork(Network network) {
        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean devMode = sharedPref.getBoolean(PreferenceNames.DeveloperMode, false);

        if (devMode) {
            return String.format("dev-msgpack/rtloc/%s", network.getId());
        } else {
            return String.format("msgpack/rtloc/%s", network.getId());
        }
    }


    public static final String ACTION_VEHICLE_ETAS_UPDATED = "im.tny.segvault.disturbances.action.vehicleeta.updated";
    public static final String EXTRA_VEHICLE_ETAS_NETWORK = "im.tny.segvault.disturbances.extra.vehicleeta.network";
}
