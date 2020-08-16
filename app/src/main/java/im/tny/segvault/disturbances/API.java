package im.tny.segvault.disturbances;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.Spannable;
import android.util.Base64;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import im.tny.segvault.disturbances.exception.APIException;

/**
 * Created by gabriel on 4/9/17.
 */

public class API {
    // there's no risk of leaking the context because we're doing context.getApplicationContext()
    // see https://stackoverflow.com/questions/39840818/android-googles-contradiction-on-singleton-pattern/39841446#39841446
    @SuppressLint("StaticFieldLeak")
    private static API singleton = new API(URI.create(BuildConfig.UNDERLX_API_ENDPOINT), 10000);

    private static SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

    public static API getInstance() {
        return singleton;
    }

    private PairManager pairManager;
    private Meta endpointMetaInfo;
    private Date endpointMetaLastUpdated;
    private final Object lock = new Object();
    private Context context;
    private long timeSkew;
    private boolean checkedTimeSkew = false;
    private final Object timeSkewLock = new Object();

    public boolean isClockOutOfSync() {
        synchronized (timeSkewLock) {
            return Math.abs(timeSkew) > TimeUnit.MINUTES.toMillis(3) && checkedTimeSkew;
        }
    }

    public void setPairManager(PairManager manager) {
        pairManager = manager;
    }

    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Meta {
        public boolean supported;
        public boolean up;
        public int minAndroidClient;
        public MOTD motd;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class MOTD {
        public Map<String, String> html;
        public String mainLocale;
        public int priority;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Gateway {
        public String protocol;

        // Capture all other fields that do not match other members
        @JsonIgnore
        private Map<String, Object> specificFields = new HashMap<String, Object>();

        @JsonAnyGetter
        public Map<String, Object> getSpecificFields() {
            return specificFields;
        }

        @JsonAnySetter
        public void setSpecificField(String name, Object value) {
            specificFields.put(name, value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Network implements Serializable {
        public String id;
        public String mainLocale;
        public Map<String, String> names;
        public int typCars;
        public List<Integer> holidays;
        public String timezone;
        public String newsURL;
        public List<String> lines;
        public List<String> stations;
        public List<Schedule> schedule;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Line implements Serializable {
        public String id;
        public String mainLocale;
        public Map<String, String> names;
        public String color;
        public String network;
        public int order;
        public int typCars;
        public List<String> stations;
        public List<Schedule> schedule;
        public List<WorldPath> worldPaths;
        public String externalID;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Station implements Serializable {
        public String id;
        public String name;
        public List<String> altNames;
        public String network;
        public List<String> tags;
        public List<String> lowTags;
        public List<String> lobbies;
        public List<String> lines;
        public List<WiFiAP> wiFiAPs;
        public Map<String, String> triviaURLs;
        public Map<String, Map<String, String>> connURLs;
        public List<String> pois;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class StationFeatures implements Serializable {
        public boolean lift;
        public boolean bus;
        public boolean boat;
        public boolean train;
        public boolean airport;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Lobby implements Serializable {
        public String id;
        public String name;
        public String network;
        public String station;
        public List<Exit> exits;
        public List<Schedule> schedule;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Exit implements Serializable {
        public int id;
        public float[] worldCoord;
        public List<String> streets;
        public String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Schedule implements Serializable {
        public boolean holiday;
        public int day;
        public boolean open;
        public int openTime;
        public int duration;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Connection implements Serializable {
        public String from;
        public String to;
        public int typWaitS;
        public int typStopS;
        public int typS;
        public int worldLength;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Transfer implements Serializable {
        public String station;
        public String from;
        public String to;
        public int typS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class DatasetInfo implements Serializable {
        public String network;
        public String version;
        public List<String> authors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class WiFiAP implements Serializable {
        public String bssid;
        public String line;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Status {
        public String id;
        public long[] time;
        public boolean downtime;
        public String status;
        public String source;
        public String msgType;
        public boolean officialSource;

        @JsonIgnore
        public Spannable enrichStatus(Context context, String networkID, String lineID) {
            return Util.enrichLineStatus(context, networkID, lineID, status, msgType, new Date(time[0] * 1000), null);
        }

        @JsonIgnore
        public boolean isOfficial() {
            return officialSource;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Disturbance {
        public String id;
        public long[] startTime;
        public long[] endTime;
        public boolean ended;
        public String description;
        public String network;
        public String line;
        public List<Status> statuses;
        public String notes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class PairRequest {
        public String nonce;
        public String timestamp;
        public String androidID;
        public String signature;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Pair {
        public String key;
        public String secret;
        public String type;
        public long[] activation;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class AuthTest {
        public String result;
        public String key;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class TripRequest {
        public String id;
        public List<StationUse> uses;
        public boolean userConfirmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Trip {
        public String id;
        public long[] startTime;
        public long[] endTime;
        public long[] submitTime;
        public long[] editTime;
        public boolean edited;
        public boolean userConfirmed;
        public List<StationUse> uses;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class StationUse {
        public String station;
        public long[] entryTime;
        public long[] leaveTime;
        public String type;
        public boolean manual;
        public String sourceLine;
        public String targetLine;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Stats {
        public Map<String, LineStats> lineStats;
        public long[] lastDisturbance;
        public int curOnInTransit;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class LineStats {
        public float availability;
        public int avgDistDuration;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class LineCondition {
        public String id;
        public long[] time;
        public int trainCars;
        public int trainFrequency;
        public String line;
        public String source;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Feedback {
        public String id;
        public long[] timestamp;
        public String type;
        public String contents;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Announcement {
        public long[] time;
        public String network;
        public String title;
        public String body;
        public String imageURL;
        public String url;
        public String source;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class RealtimeLocationRequest {
        public String s; // current station ID
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String d; // current direction ID, or null if none
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class POI implements Serializable {
        public String id;
        public String type;
        public float[] worldCoord;
        public String webURL;
        public String mainLocale;
        public Map<String, String> names;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class WorldPath implements Serializable {
        public String id;
        public List<float[]> path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class DisturbanceReport implements Serializable {
        public String line;
        public String category;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class PairConnectionRequest {
        public String code;
        public String deviceName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class PairConnectionResponse {
        public String result;
        public String serviceName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class PairConnection {
        public String service;
        public String serviceName;
        public long[] creationTime;
        public Object extra;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = WorldMap.class, name = "world-map"),
            @JsonSubTypes.Type(value = HTMLMap.class, name = "html")}
    )
    static public class Diagram implements Serializable {
        public String type;
    }

    static public class WorldMap extends Diagram {
    }

    static public class HTMLMap extends Diagram {
        public String url;
        public boolean cache;
        public boolean wideViewport;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class PosPlayStatus implements Serializable {
        public String serviceName;

        public long discordID;
        public String username;
        public String avatarURL;
        public int level;
        public float levelProgress;
        public int xp;
        public int xpThisWeek;
        public int rank;
        public int rankThisWeek;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class AndroidClientConfigs implements Serializable {
        public Map<String, String> helpOverlays;
        public Map<String, String> infra;
    }

    //region MQTT payloads
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = MQTTvehicleETAnoValue.class, name = "n"),
            @JsonSubTypes.Type(value = MQTTvehicleETAsingleValue.class, name = "e"),
            @JsonSubTypes.Type(value = MQTTvehicleETAinterval.class, name = "i"),
            @JsonSubTypes.Type(value = MQTTvehicleETAsingleValue.class, name = "l"),
            @JsonSubTypes.Type(value = MQTTvehicleETAsingleValue.class, name = "m"),
            @JsonSubTypes.Type(value = MQTTvehicleETAsingleValue.class, name = "t")}
    )
    static public abstract class MQTTvehicleETA {
        public String direction;
        public long made;
        public int validFor;
        public String type;
        public String units;
        public int order;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class MQTTvehicleETAnoValue extends MQTTvehicleETA {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class MQTTvehicleETAsingleValue extends MQTTvehicleETA {
        public long value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class MQTTvehicleETAinterval extends MQTTvehicleETA {
        public int lower;
        public int upper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class MQTTvehiclePosition {
        @JsonProperty("v")
        public String vehicle;

        @JsonProperty("p")
        public String prevStation;

        @JsonProperty("n")
        public String nextStation;

        @JsonProperty("d")
        public String direction;

        @JsonProperty("t")
        public String platform;

        @JsonProperty("c")
        public int percent;

        @JsonProperty("m")
        public long made;

        @JsonProperty("f")
        public int validFor;
    }

    //endregion

    private int timeoutMs;
    private URI endpoint;

    public URI getEndpoint() {
        return endpoint;
    }

    private API(URI endpoint, int timeoutMs) {
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
    }

    private ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    public ObjectMapper getMapper() {
        return mapper;
    }

    private InputStream getRequest(URI uri, boolean authenticate) throws APIException {
        Date requestStart = new Date();
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();
            h.setUseCaches(false);
            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            setCommonRequestProperties(h, authenticate);
            h.setRequestMethod("GET");
            h.setDoInput(true);

            InputStream is;
            int code;
            try {
                // Will throw IOException if server responds with 401.
                code = h.getResponseCode();
            } catch (IOException e) {
                // Will return 401, because now connection has the correct internal state.
                code = h.getResponseCode();
            }
            if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                is = h.getInputStream();
            } else {
                is = h.getErrorStream();
            }

            long serverTime = h.getDate();
            if(serverTime != 0) {
                long requestDuration = new Date().getTime() - requestStart.getTime();
                synchronized (timeSkewLock) {
                    timeSkew = requestStart.getTime() + requestDuration / 2 - serverTime;
                    checkedTimeSkew = true;
                }
            }

            if ("gzip".equals(h.getContentEncoding())) {
                return new GZIPInputStream(is);
            }
            return is;
        } catch (MalformedURLException e) {
            throw new APIException(e).addInfo("Malformed URL on GET request");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException on GET request");
        }
    }

    private Map<String, List<String>> headRequest(URI uri, boolean authenticate) throws APIException {
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();
            h.setUseCaches(false);
            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            setCommonRequestProperties(h, authenticate);
            h.setRequestMethod("HEAD");
            h.setDoInput(true);

            InputStream is;
            int code;
            try {
                // Will throw IOException if server responds with 401.
                code = h.getResponseCode();
            } catch (IOException e) {
                // Will return 401, because now connection has the correct internal state.
                code = h.getResponseCode();
            }
            if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                is = h.getInputStream();
            } else {
                is = h.getErrorStream();
            }
            if (is != null) {
                is.close();
            }
            return h.getHeaderFields();
        } catch (MalformedURLException e) {
            throw new APIException(e).addInfo("Malformed URL on GET request");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException on GET request");
        }
    }

    private InputStream doInputRequest(URI uri, byte[] content, boolean authenticate, String method) throws APIException {
        Date requestStart = new Date();
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();
            h.setUseCaches(false);
            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            h.setRequestProperty("Content-Type", "application/msgpack");
            setCommonRequestProperties(h, authenticate);

            h.setRequestMethod(method);
            h.setDoInput(true);
            h.setDoOutput(true);

            h.setFixedLengthStreamingMode(content.length);
            OutputStream out = new BufferedOutputStream(h.getOutputStream());
            out.write(content);
            out.close();

            InputStream is;
            int code;
            try {
                // Will throw IOException if server responds with 401.
                code = h.getResponseCode();
            } catch (IOException e) {
                // Will return 401, because now connection has the correct internal state.
                code = h.getResponseCode();
            }
            if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                is = h.getInputStream();
            } else {
                is = h.getErrorStream();
            }

            long serverTime = h.getDate();
            if(serverTime != 0) {
                long requestDuration = new Date().getTime() - requestStart.getTime();
                synchronized (timeSkewLock) {
                    timeSkew = requestStart.getTime() + requestDuration / 2 - serverTime;
                    checkedTimeSkew = true;
                }
            }

            if ("gzip".equals(h.getContentEncoding())) {
                return new GZIPInputStream(is);
            }
            return is;
        } catch (MalformedURLException e) {
            throw new APIException(e).addInfo("Malformed URL on " + method + " request");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException on " + method + " request");
        }
    }

    private InputStream postRequest(URI uri, byte[] content, boolean authenticate) throws APIException {
        return doInputRequest(uri, content, authenticate, "POST");
    }

    private InputStream putRequest(URI uri, byte[] content, boolean authenticate) throws APIException {
        return doInputRequest(uri, content, authenticate, "PUT");
    }

    private void setCommonRequestProperties(HttpURLConnection h, boolean authenticate) throws UnsupportedEncodingException {
        h.setRequestProperty("Accept", "application/msgpack");
        h.setRequestProperty("Accept-Encoding", "gzip");
        if (authenticate && pairManager != null) {
            String toEncode = pairManager.getPairKey() + ":" + pairManager.getPairSecret();
            h.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(toEncode.getBytes("UTF-8"), Base64.NO_WRAP));
        }
        h.setRequestProperty("User-Agent",
                String.format(Locale.US, "UnderLX/%s#%d %s",
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        System.getProperty("http.agent")));
    }

    private Meta getMetaOnline() throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("meta"), false), Meta.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Meta getMeta() throws APIException {
        return getMeta(false);
    }

    public Meta getMeta(boolean forceUpdate) throws APIException {
        synchronized (lock) {
            if (this.endpointMetaInfo == null || forceUpdate) {
                this.endpointMetaInfo = getMetaOnline();
                this.endpointMetaLastUpdated = new Date();
                if (context != null) {
                    Intent intent = new Intent(ACTION_ENDPOINT_META_AVAILABLE);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }
                getMQTTConnectionInfo();
            }
            return this.endpointMetaInfo;
        }
    }

    @Nullable
    public Meta getMetaOffline() {
        return this.endpointMetaInfo;
    }

    @Nullable
    public Date getMetaLastUpdated() {
        return this.endpointMetaLastUpdated;
    }

    public boolean meetsEndpointRequirements(boolean goOnline) {
        if (!goOnline) {
            synchronized (lock) {
                if (endpointMetaInfo == null) {
                    return true;
                }
                return BuildConfig.VERSION_CODE >= endpointMetaInfo.minAndroidClient;
            }
        }
        try {
            Meta meta = getMeta();
            return BuildConfig.VERSION_CODE >= meta.minAndroidClient;
        } catch (APIException ex) {
            return true;
        }
    }

    private void throwIfRequirementsNotMet() throws APIException {
        if (!meetsEndpointRequirements(true)) {
            throw new APIException(new Exception("This API client does not meet endpoint requirements"));
        }
        if (this.endpointMetaInfo != null) {
            if (!this.endpointMetaInfo.up) {
                throw new APIException(new Exception("Endpoint reports it is not operating"));
            }
            if (!this.endpointMetaInfo.supported) {
                throw new APIException(new Exception("Endpoint reports it is not supported"));
            }
        }
    }

    public List<Gateway> getGateways() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("gateways"), false), new TypeReference<List<Gateway>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public class MQTTConnectionInfo {
        public String host;
        public int port;
        public boolean isTLS;
        public String protocolVersion;
    }

    private MQTTConnectionInfo mqttConnInfo = null;

    public MQTTConnectionInfo getMQTTConnectionInfo() {
        synchronized (lock) {
            if (mqttConnInfo != null) {
                return mqttConnInfo;
            }
        }
        try {
            List<API.Gateway> gateways = getGateways();

            for (API.Gateway gateway : gateways) {
                if (!gateway.protocol.equals("mqtt")) {
                    continue;
                }
                if (!"3.1.1".equals(gateway.getSpecificFields().get("pVer"))) {
                    continue;
                }
                if (gateway.getSpecificFields().containsKey("host") && gateway.getSpecificFields().get("host") instanceof String &&
                        gateway.getSpecificFields().containsKey("port") && gateway.getSpecificFields().get("port") instanceof Integer) {
                    MQTTConnectionInfo info = new MQTTConnectionInfo();
                    info.host = (String) gateway.getSpecificFields().get("host");
                    info.port = (Integer) gateway.getSpecificFields().get("port");
                    info.isTLS = gateway.getSpecificFields().containsKey("tls") && gateway.getSpecificFields().get("tls").equals(true);
                    info.protocolVersion = (String) gateway.getSpecificFields().get("pVer");
                    mqttConnInfo = info;
                    return info;
                }
            }
        } catch (APIException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Network> getNetworks() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("networks"), false), new TypeReference<List<Network>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Network getNetwork(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("networks/" + id), false), Network.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Station> getStations() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("stations"), false), new TypeReference<List<Station>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Station getStation(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("stations/" + id), false), Station.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Lobby> getLobbies() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lobbies"), false), new TypeReference<List<Lobby>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Lobby getLobby(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lobbies/" + id), false), Lobby.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Connection> getConnections() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("connections"), false), new TypeReference<List<Connection>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Transfer> getTransfers() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("transfers"), false), new TypeReference<List<Transfer>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Line getLine(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lines/" + id), false), Line.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<DatasetInfo> getDatasetInfos() throws APIException {
        throwIfRequirementsNotMet();
        try {
            List<DatasetInfo> l = mapper.readValue(getRequest(endpoint.resolve("datasets"), false), new TypeReference<List<DatasetInfo>>() {
            });
            for (DatasetInfo i : l) {
                if (i.version == null || i.authors == null || i.network == null) {
                    throw new APIException(new IOException()).addInfo("Response missing required field");
                }
            }
            return l;
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public DatasetInfo getDatasetInfo(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            DatasetInfo i = mapper.readValue(getRequest(endpoint.resolve("datasets/" + id), false), DatasetInfo.class);
            if (i.version == null || i.authors == null || i.network == null) {
                throw new APIException(new IOException()).addInfo("Response missing required field");
            }
            return i;
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Disturbance> getDisturbances() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("disturbances?omitduplicatestatus=true"), false), new TypeReference<List<Disturbance>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Disturbance> getOngoingDisturbances() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("disturbances?omitduplicatestatus=true&filter=ongoing"), false), new TypeReference<List<Disturbance>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Disturbance> getDisturbancesSince(Date since) throws APIException {
        throwIfRequirementsNotMet();
        try {
            String url = String.format("disturbances?omitduplicatestatus=true&start=%s",
                    URLEncoder.encode(Util.encodeRFC3339(since), "utf-8"));
            return mapper.readValue(getRequest(endpoint.resolve(url), false), new TypeReference<List<Disturbance>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Disturbance getDisturbance(String id) throws APIException {
        throwIfRequirementsNotMet();
        try {
            String url = String.format("disturbances/%s?omitduplicatestatus=true", id);
            return mapper.readValue(getRequest(endpoint.resolve(url), false), Disturbance.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Pair postPairRequest(PairRequest request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = postRequest(endpoint.resolve("pair"), content, false);
            return mapper.readValue(is, Pair.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public AuthTest getAuthTest() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("authtest"), true), AuthTest.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Trip postTrip(TripRequest request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = postRequest(endpoint.resolve("trips"), content, true);
            return mapper.readValue(is, Trip.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Trip putTrip(TripRequest request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = putRequest(endpoint.resolve("trips"), content, true);
            return mapper.readValue(is, Trip.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Stats getStats(String networkID, Date since, Date until) throws APIException {
        throwIfRequirementsNotMet();
        try {
            String url = String.format("stats/%s?start=%s&end=%s",
                    networkID, URLEncoder.encode(Util.encodeRFC3339(since), "utf-8"), URLEncoder.encode(Util.encodeRFC3339(until), "utf-8"));
            return mapper.readValue(getRequest(endpoint.resolve(url), false), Stats.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<LineCondition> getLatestLineConditions() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lines/conditions?filter=latest"), false), new TypeReference<List<LineCondition>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Feedback postFeedback(Feedback request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = postRequest(endpoint.resolve("feedback"), content, true);
            return mapper.readValue(is, Feedback.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Announcement> getAnnouncements() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("announcements"), false), new TypeReference<List<Announcement>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Announcement> getAnnouncementsFromSource(String source) throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("announcements/" + source), false), new TypeReference<List<Announcement>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<POI> getPOIs() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("pois"), false), new TypeReference<List<POI>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Feedback postRealtimeLocation(RealtimeLocationRequest request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = postRequest(endpoint.resolve("rt"), content, true);
            return mapper.readValue(is, Feedback.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public void postDisturbanceReport(DisturbanceReport report) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(report);
            postRequest(endpoint.resolve("disturbances/reports"), content, true);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Date getBackersLastModified(String locale) throws APIException {
        throwIfRequirementsNotMet();
        Map<String, List<String>> headers = headRequest(endpoint.resolve("meta/backers/?locale=" + locale), false);
        if (headers.get("Last-Modified") == null || headers.get("Last-Modified").size() == 0) {
            throw new APIException(new IOException("Last-Modified not present in API response"));
        }
        String lastModified = headers.get("Last-Modified").get(0);
        try {
            return httpDateFormat.parse(lastModified);
        } catch (ParseException e) {
            throw new APIException(e).addInfo("ParseException");
        }
    }

    public String getBackers(String locale) throws APIException {
        return getFileRelativeToEndpoint("meta/backers/?locale=" + locale);
    }

    public String getFileRelativeToEndpoint(String relativePath) throws APIException {
        throwIfRequirementsNotMet();
        BufferedReader r = null;
        try {
            InputStream is = getRequest(endpoint.resolve(relativePath), false);
            r = new BufferedReader(new InputStreamReader(is));
            StringBuilder total = new StringBuilder(is.available());
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line).append('\n');
            }
            return total.toString();
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException e) {
                    throw new APIException(e).addInfo("IOException");
                }
            }
        }
    }

    public PairConnectionResponse postPairConnectionRequest(PairConnectionRequest request) throws APIException {
        throwIfRequirementsNotMet();
        try {
            byte[] content = mapper.writeValueAsBytes(request);
            InputStream is = postRequest(endpoint.resolve("pair/connections"), content, true);
            return mapper.readValue(is, PairConnectionResponse.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<PairConnection> getPairConnections() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("pair/connections"), true), new TypeReference<List<PairConnection>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Diagram> getDiagrams() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("maps"), false), new TypeReference<List<Diagram>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public AndroidClientConfigs getAndroidClientConfigs() throws APIException {
        throwIfRequirementsNotMet();
        try {
            return mapper.readValue(getRequest(endpoint.resolve("clientconfigs/android"), false), AndroidClientConfigs.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public static final String ACTION_ENDPOINT_META_AVAILABLE = "im.tny.segvault.disturbances.action.API.metadownloaded";
}
