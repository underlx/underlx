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
import java.util.Arrays;
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
    private Set<String> mqttSubscriptions = new HashSet<>();

    public MqttManager(Context context) {
        this.context = context;
    }

    private static class MQTTConnectTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private String[] initialTopics;

        MQTTConnectTask(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
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
            synchronized (parent.mqttLock) {
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

                parent.mqttConnection = mqttClient.blockingConnection();
                try {
                    parent.mqttConnection.connect();
                } catch (Exception e) {
                    // oh well
                    return null;
                }
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
            new MQTTSubscribeTask(parent).execute(initialTopics);
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
            synchronized (parent.mqttLock) {
                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }

                try {
                    parent.mqttConnection.disconnect();
                } catch (Exception e) {
                    // oh well
                    e.printStackTrace();
                }
                return null;
            }
        }
    }

    private static class MQTTSubscribeTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;
        private String unsubscribeFromPrefix = "";

        MQTTSubscribeTask(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        MQTTSubscribeTask(MqttManager parent, String unsubscribeFromPrefix) {
            parentRef = new WeakReference<>(parent);
            this.unsubscribeFromPrefix = unsubscribeFromPrefix;
        }

        @Override
        protected Void doInBackground(String... topics) {
            MqttManager parent = parentRef.get();
            if (parent == null || topics.length == 0) {
                return null;
            }
            synchronized (parent.mqttLock) {
                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }

                if (!unsubscribeFromPrefix.isEmpty()) {
                    List<String> subs = new ArrayList<>();
                    for (String sub : parent.mqttSubscriptions) {
                        if (sub.startsWith(unsubscribeFromPrefix)) {
                            subs.add(sub);
                        }
                    }

                    if (subs.size() > 0) {
                        String arr[] = new String[subs.size()];
                        try {
                            parent.mqttConnection.unsubscribe(subs.toArray(arr));
                            parent.mqttSubscriptions.removeAll(subs);
                        } catch (Exception e) {
                            // oh well
                            e.printStackTrace();
                        }
                    }
                }

                List<Topic> mqttTopics = new ArrayList<>();
                for (String topic : topics) {
                    mqttTopics.add(new Topic(topic, QoS.AT_MOST_ONCE));
                }
                Topic arr[] = new Topic[mqttTopics.size()];
                try {
                    parent.mqttConnection.subscribe(mqttTopics.toArray(arr));
                    parent.mqttSubscriptions.addAll(Arrays.asList(topics));
                } catch (Exception e) {
                    // oh well
                    e.printStackTrace();
                }
                return null;
            }
        }
    }

    private static class MQTTUnsubscribeTask extends AsyncTask<String, Void, Void> {
        private WeakReference<MqttManager> parentRef;

        MQTTUnsubscribeTask(MqttManager parent) {
            parentRef = new WeakReference<>(parent);
        }

        @Override
        protected Void doInBackground(String... topics) {
            MqttManager parent = parentRef.get();
            if (parent == null || topics.length == 0) {
                return null;
            }
            synchronized (parent.mqttLock) {
                if (parent.mqttConnection == null || !parent.mqttConnection.isConnected()) {
                    return null;
                }

                try {
                    parent.mqttConnection.unsubscribe(topics);
                    parent.mqttSubscriptions.removeAll(Arrays.asList(topics));
                } catch (Exception e) {
                    // oh well
                    e.printStackTrace();
                }

                return null;
            }
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
                if(!connection.isConnected()) {
                    return;
                }
                MapManager mapManager = Coordinator.get(parent.context).getMapManager();

                try {
                    Message message = connection.receive(10, TimeUnit.SECONDS);
                    if(message == null) {
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
                }
            }
        }
    }

    void connect(String... initialTopics) {
            new MQTTConnectTask(this).execute(initialTopics);
    }

    void disconnect() {
            new MQTTDisconnectTask(this).execute();
    }

    void subscribe(String... topics) {
            new MQTTSubscribeTask(this).execute(topics);
    }

    void replaceSubscriptionsWithPrefix(String prefix, String... newTopics) {
            new MQTTSubscribeTask(this, prefix).execute(newTopics);
    }

    void unsubscribe(String... topics) {
            new MQTTUnsubscribeTask(this).execute(topics);
    }

    void unsubscribeAll() {
        synchronized (mqttLock) {
            if (mqttSubscriptions.size() == 0) {
                return;
            }
            String arr[] = new String[mqttSubscriptions.size()];
            new MQTTUnsubscribeTask(this).execute(mqttSubscriptions.toArray(arr));
        }
    }

    void unsubscribeWithPrefix(String prefix) {
        synchronized (mqttLock) {
            List<String> subs = new ArrayList<>();
            for (String sub : mqttSubscriptions) {
                if (sub.startsWith(prefix)) {
                    subs.add(sub);
                }
            }
            if (subs.size() == 0) {
                return;
            }
            String arr[] = new String[subs.size()];
            new MQTTUnsubscribeTask(this).execute(subs.toArray(arr));
        }
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

        Log.d("MQTT", "Station " + station.getId());

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

        if(devMode) {
            return "dev-msgpack/vehicleeta/";
        } else {
            return "msgpack/vehicleeta/";
        }
    }

    public static final String ACTION_VEHICLE_ETAS_UPDATED = "im.tny.segvault.disturbances.action.vehicleeta.updated";
    public static final String EXTRA_VEHICLE_ETAS_NETWORK = "im.tny.segvault.disturbances.extra.vehicleeta.network";
}
