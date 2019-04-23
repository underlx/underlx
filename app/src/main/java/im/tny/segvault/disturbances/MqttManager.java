package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

import static android.content.Context.MODE_PRIVATE;

public class MqttManager {
    private Context context;
    private final Object mqttLock = new Object();
    private BlockingConnection mqttConnection;
    private Map<Integer, HashSet<String>> mqttSubscriptionsByParty = new HashMap<>();
    private Map<String, HashSet<Integer>> mqttSubscriptionsByTopic = new HashMap<>();

    public MqttManager(Context context) {
        this.context = context;
    }

    private static class MQTTConnectTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private String[] initialTopics;
        private int clientID;

        MQTTConnectTask(MqttManager parent, int clientID) {
            parentRef = new WeakReference<>(parent);
            this.clientID = clientID;
        }

        @Override
        protected Void doInBackground(String... initialTopics) {
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return null;
            }
            if (!Coordinator.get(parent.context).getPairManager().isPaired()) {
                return null;
            }
            this.initialTopics = initialTopics;

            API.MQTTConnectionInfo info = API.getInstance().getMQTTConnectionInfo();

            if (info == null) {
                return null;
            }

            MQTT mqttClient = new MQTT();
            try {
                if (info.isTLS) {
                    mqttClient.setHost(String.format("tls://%s:%d", info.host, info.port));
                } else {
                    mqttClient.setHost(String.format("tcp://%s:%d", info.host, info.port));
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return null;
            }
            mqttClient.setClientId(String.format("%s-%d", Coordinator.get(parent.context).getPairManager().getPairKey(), new Date().getTime()));
            mqttClient.setUserName(Coordinator.get(parent.context).getPairManager().getPairKey());
            mqttClient.setPassword(Coordinator.get(parent.context).getPairManager().getPairSecret());
            mqttClient.setVersion(info.protocolVersion);
            mqttClient.setReconnectDelay(1000);

            BlockingConnection connection = mqttClient.blockingConnection();
            try {
                connection.connect();
            } catch (Exception e) {
                // oh well
                return null;
            }

            synchronized (parent.mqttLock) {
                parent.mqttConnection = connection;
                Log.d("MQTT", "connected");
                new Thread(new ConsumeRunnable(parent)).start();

                return null;
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            MqttManager parent = parentRef.get();
            if (parent == null) {
                return;
            }
            new MQTTSubscribeTask(parent, clientID).execute(initialTopics);
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
            BlockingConnection connection;
            synchronized (parent.mqttLock) {
                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }
                connection = parent.mqttConnection;

                parent.mqttSubscriptionsByTopic.clear();
                parent.mqttSubscriptionsByParty.clear();

                // setting this to null tells the message consumption thread to stop
                parent.mqttConnection = null;

            }

            try {
                connection.disconnect();
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
            if (parent == null || topics.length == 0) {
                return null;
            }
            BlockingConnection connection;
            synchronized (parent.mqttLock) {
                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }
                connection = parent.mqttConnection;
            }

            List<Topic> mqttTopics = new ArrayList<>();
            for (String topic : topics) {
                mqttTopics.add(new Topic(topic, QoS.AT_MOST_ONCE));
            }
            Topic arr[] = new Topic[mqttTopics.size()];
            try {
                connection.subscribe(mqttTopics.toArray(arr));
            } catch (Exception e) {
                // oh well
                e.printStackTrace();
                return null;
            }

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
            if (parent == null || topics.length == 0) {
                return null;
            }
            List<String> actualTopics = new ArrayList<>();
            BlockingConnection connection;
            synchronized (parent.mqttLock) {
                connection = parent.mqttConnection;

                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
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
                        byTopic.remove(partyID);
                        byParty.remove(topic);
                    }
                }
            }

            if (actualTopics.size() == 0) {
                return null;
            }

            String arr[] = new String[actualTopics.size()];
            try {
                connection.unsubscribe(actualTopics.toArray(arr));
            } catch (Exception e) {
                // oh well
                e.printStackTrace();
                return null;
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
                    }
                    byParty.remove(topic);
                }
            }

            return null;
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

            BlockingConnection connection;
            synchronized (parent.mqttLock) {
                connection = parent.mqttConnection;

                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }
            }

            try {
                connection.publish(topic, payload, QoS.AT_MOST_ONCE, false);
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

        public void run() {
            while (true) {
                MqttManager parent = parentRef.get();
                if (parent == null) {
                    return;
                }

                BlockingConnection connection;
                synchronized (parent.mqttLock) {
                    connection = parent.mqttConnection;
                }
                if (connection == null) {
                    return;
                }
                try {
                    if (!connection.isConnected()) {
                        Thread.sleep(2000);
                        continue;
                    }
                    MapManager mapManager = Coordinator.get(parent.context).getMapManager();

                    Message message = connection.receive(10, TimeUnit.SECONDS);
                    if (message == null) {
                        continue;
                    }
                    message.ack();
                    String topicName = message.getTopic();
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
                        parent.processVehicleETAMessage(station, message.getPayload());
                        Intent intent = new Intent(ACTION_VEHICLE_ETAS_UPDATED);
                        intent.putExtra(EXTRA_VEHICLE_ETAS_NETWORK, network.getId());
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(parent.context);
                        bm.sendBroadcast(intent);
                    }
                } catch (Exception e) {
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

    private int partyCount = 0;
    private int currPartyID = 0;

    public int connect(String... initialTopics) {
        synchronized (mqttLock) {
            int id = currPartyID++;
            if (partyCount == 0) {
                new MQTTConnectTask(this, currPartyID).execute(initialTopics);
            } else {
                subscribe(currPartyID, initialTopics);
            }
            partyCount++;
            return id;
        }
    }

    public void disconnect(int partyID) {
        synchronized (mqttLock) {
            if (partyCount <= 1) {
                new MQTTDisconnectTask(this).execute();
            } else {
                HashSet<String> byParty = mqttSubscriptionsByParty.get(partyID);
                if (byParty != null && byParty.size() > 0) {
                    String arr[] = new String[byParty.size()];
                    new MQTTUnsubscribeTask(this, partyID).execute(byParty.toArray(arr));
                }
            }
            partyCount--;
        }
    }

    public void subscribe(int partyID, String... topics) {
        new MQTTSubscribeTask(this, partyID).execute(topics);
    }

    public void unsubscribe(int partyID, String... topics) {
        new MQTTUnsubscribeTask(this, partyID).execute(topics);
    }

    public void unsubscribeAll(int partyID) {
        synchronized (mqttLock) {
            Set<String> subs = mqttSubscriptionsByParty.get(partyID);
            if (subs.size() == 0) {
                return;
            }
            String arr[] = new String[subs.size()];
            new MQTTUnsubscribeTask(this, partyID).execute(subs.toArray(arr));
        }
    }

    public void publish(int partyID, String topic, byte[] payload) {
        new MQTTPublishTask(this, topic, payload).execute();
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
        return String.format("%s%s/%s", getVehicleETAsTopicPrefix(), station.getNetwork().getId(), station.getId());
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
