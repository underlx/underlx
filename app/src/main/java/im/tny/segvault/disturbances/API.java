package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import im.tny.segvault.disturbances.exception.APIException;

/**
 * Created by gabriel on 4/9/17.
 */

public class API {
    private static API singleton = new API(URI.create("https://api.perturbacoes.tny.im/v1/"), 10000);
    //private static API singleton = new API(URI.create("http://10.0.3.2:12000/v1/"), 10000);

    public static API getInstance() {
        return singleton;
    }

    private PairManager pairManager;
    private Meta endpointMetaInfo;
    private Object lock = new Object();
    private Context context;

    public void setPairManager(PairManager manager) {
        pairManager = manager;
    }
    public void setContext(Context context) { this.context = context; }


    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Meta {
        public boolean supported;
        public boolean up;
        public int minAndroidClient;
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

    private InputStream getRequest(URI uri, boolean authenticate) throws APIException {
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();

            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            h.setRequestProperty("Accept", "application/msgpack");
            h.setRequestProperty("Accept-Encoding", "gzip");
            if (authenticate && pairManager != null) {
                String toEncode = pairManager.getPairKey() + ":" + pairManager.getPairSecret();
                h.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(toEncode.getBytes("UTF-8"), Base64.NO_WRAP));
            }
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

    private InputStream doInputRequest(URI uri, byte[] content, boolean authenticate, String method) throws APIException {
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();

            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            h.setRequestProperty("Accept", "application/msgpack");
            h.setRequestProperty("Accept-Encoding", "gzip");
            h.setRequestProperty("Content-Type", "application/msgpack");
            if (authenticate && pairManager != null) {
                String toEncode = pairManager.getPairKey() + ":" + pairManager.getPairSecret();
                h.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(toEncode.getBytes("UTF-8"), Base64.NO_WRAP));
            }

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
        synchronized (lock) {
            if (this.endpointMetaInfo == null) {
                this.endpointMetaInfo = getMetaOnline();
                if(context != null) {
                    Intent intent = new Intent(ACTION_ENDPOINT_META_AVAILABLE);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }
            }
            return this.endpointMetaInfo;
        }
    }

    public boolean meetsEndpointRequirements(boolean goOnline) {
        if(!goOnline) {
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
            return mapper.readValue(getRequest(endpoint.resolve("datasets"), false), new TypeReference<List<DatasetInfo>>() {
            });
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
            return mapper.readValue(getRequest(endpoint.resolve("datasets/" + id), false), DatasetInfo.class);
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

    public static final String ACTION_ENDPOINT_META_AVAILABLE = "im.tny.segvault.disturbances.action.API.metadownloaded";
}
