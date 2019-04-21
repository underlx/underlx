package im.tny.segvault.disturbances;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.Mqtt3ReturnCode;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe;
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3UnsubscribeBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

public class MqttManager {
    private Context context;
    private final Object mqttLock = new Object();
    private Mqtt3BlockingClient mqttClient = null;
    private boolean mqttConnected = false;
    private Set<String> mqttSubscriptions = new HashSet<>();

    public MqttManager(Context context) {
        this.context = context;
    }

    @TargetApi(Build.VERSION_CODES.N)
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

                Mqtt3ClientBuilder builder = Mqtt3Client.builder()
                        .identifier(Coordinator.get(parent.context).getPairManager().getPairKey())
                        .serverHost(info.host)
                        .serverPort(info.port);
                if (info.isTLS) {
                    builder = builder.useSslWithDefaultConfig();
                }
                parent.mqttClient = builder.buildBlocking();

                String user = Coordinator.get(parent.context).getPairManager().getPairKey();
                byte[] pass;
                try {
                    pass = Coordinator.get(parent.context).getPairManager().getPairSecret().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // this just doesn't happen
                    return null;
                }

                Mqtt3ReturnCode connectCode = parent.mqttClient.connectWith()
                        .simpleAuth()
                        .username(user)
                        .password(pass)
                        .applySimpleAuth()
                        .send().getReturnCode();

                if (connectCode.isError()) {
                    return null;
                }
                parent.mqttConnected = true;
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

    @TargetApi(Build.VERSION_CODES.N)
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
                if (parent.mqttClient == null || !parent.mqttConnected) {
                    return null;
                }

                try {
                    parent.mqttClient.disconnect();
                } catch (MqttClientStateException ex) {
                    // probably we were not connected anymore
                }
                return null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
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
                if (parent.mqttClient == null || !parent.mqttConnected) {
                    return null;
                }

                if (!unsubscribeFromPrefix.isEmpty()) {
                    List<String> subs = new ArrayList<>();
                    for (String sub : parent.mqttSubscriptions) {
                        if (sub.startsWith(unsubscribeFromPrefix)) {
                            subs.add(sub);
                        }
                    }

                    Mqtt3UnsubscribeBuilder builder = Mqtt3Unsubscribe.builder();
                    Mqtt3UnsubscribeBuilder.Complete completeBuilder = null;
                    for (String topic : subs) {
                        parent.mqttSubscriptions.remove(topic);
                        if (completeBuilder == null) {
                            completeBuilder = builder.addTopicFilter(topic);
                        } else {
                            completeBuilder = completeBuilder.addTopicFilter(topic);
                        }
                    }
                    try {
                        parent.mqttClient.unsubscribe(completeBuilder.build());
                    } catch (MqttClientStateException ex) {
                        // TODO handle disconnects by reconnecting
                        // probably we were not connected anymore
                    }
                }

                Mqtt3SubscribeBuilder builder = Mqtt3Subscribe.builder();
                Mqtt3SubscribeBuilder.Complete completeBuilder = null;
                for (String topic : topics) {
                    Mqtt3Subscription sub = Mqtt3Subscription.builder().topicFilter(topic).qos(MqttQos.AT_MOST_ONCE).build();
                    if (completeBuilder == null) {
                        completeBuilder = builder.addSubscription(sub);
                    } else {
                        completeBuilder = completeBuilder.addSubscription(sub);
                    }
                    parent.mqttSubscriptions.add(topic);
                }
                try {
                    parent.mqttClient.subscribe(completeBuilder.build());
                } catch (MqttClientStateException ex) {
                    // TODO handle disconnects by reconnecting
                    // probably we were not connected anymore
                }

                return null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
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
                if (parent.mqttClient == null || !parent.mqttConnected) {
                    return null;
                }

                Mqtt3UnsubscribeBuilder builder = Mqtt3Unsubscribe.builder();
                Mqtt3UnsubscribeBuilder.Complete completeBuilder = null;
                for (String topic : topics) {
                    parent.mqttSubscriptions.remove(topic);
                    if (completeBuilder == null) {
                        completeBuilder = builder.addTopicFilter(topic);
                    } else {
                        completeBuilder = completeBuilder.addTopicFilter(topic);
                    }
                }
                try {
                    parent.mqttClient.unsubscribe(completeBuilder.build());
                } catch (MqttClientStateException ex) {
                    // TODO handle disconnects by reconnecting
                    // probably we were not connected anymore
                }

                return null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
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

                Mqtt3BlockingClient client;
                synchronized (parent.mqttLock) {
                    client = parent.mqttClient;
                    if (!parent.mqttConnected) {
                        return;
                    }
                }
                MapManager mapManager = Coordinator.get(parent.context).getMapManager();

                try (Mqtt3BlockingClient.Mqtt3Publishes publishes = client.publishes(MqttGlobalPublishFilter.ALL)) {
                    Optional<Mqtt3Publish> publishMessage = publishes.receive(10, TimeUnit.SECONDS);
                    if (!publishMessage.isPresent()) {
                        continue;
                    }
                    Mqtt3Publish publish = publishMessage.get();
                    String topicName = publish.getTopic().toString();
                    Log.d("MQTT", "Received message on topic " + topicName);

                    if (topicName.startsWith("msgpack/vehicleeta/")) {
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
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (MqttSessionExpiredException e) {
                    return;
                }
            }
        }
    }

    void connect(String... initialTopics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            new MQTTConnectTask(this).execute(initialTopics);
        }
    }

    void disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            new MQTTDisconnectTask(this).execute();
        }
    }

    void subscribe(String... topics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            new MQTTSubscribeTask(this).execute(topics);
        }
    }

    void replaceSubscriptionsWithPrefix(String prefix, String... newTopics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            new MQTTSubscribeTask(this, prefix).execute(newTopics);
        }
    }

    void unsubscribe(String... topics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            new MQTTUnsubscribeTask(this).execute(topics);
        }
    }

    void unsubscribeAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        synchronized (mqttLock) {
            if (mqttSubscriptions.size() == 0) {
                return;
            }
            String arr[] = new String[mqttSubscriptions.size()];
            new MQTTUnsubscribeTask(this).execute(mqttSubscriptions.toArray(arr));
        }
    }

    void unsubscribeWithPrefix(String prefix) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
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

    // returns a map where the key is the direction
    public Map<Station, API.MQTTvehicleETA> getVehicleETAsForStation(Station station) {
        Map<Station, API.MQTTvehicleETA> result = new HashMap<>();
        synchronized (vehicleETAsLock) {
            for (String key : vehicleETAs.keySet()) {
                String[] parts = key.split("#");
                if (parts[0].equals(station.getNetwork().getId()) && parts[1].equals(station.getId())) {
                    VehicleETAValue value = vehicleETAs.get(key);
                    if (new Date().getTime() - value.storedAt.getTime() > TimeUnit.SECONDS.toMillis(value.eta.validFor)) {
                        // do not include expired ones
                        continue;
                    }
                    result.put(station.getNetwork().getStation(parts[2]), value.eta);
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

    public static String getVehicleETAsTopicForStation(Station station) {
        return String.format("%s%s/%s", getVehicleETAsTopicPrefix(), station.getNetwork().getId(), station.getId());
    }

    public static String getVehicleETAsTopicPrefix() {
        return "msgpack/vehicleeta/";
    }

}
