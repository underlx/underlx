package im.tny.segvault.disturbances;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import im.tny.segvault.disturbances.exception.APIException;

/**
 * Created by gabriel on 4/9/17.
 */

public class API {
    private static API singleton = new API(URI.create("https://api.perturbacoes.tny.im/v1/"), 10000);
    //private static API singleton = new API(URI.create("http://192.168.10.193:12000/v1/"), 10000);

    public static API getInstance() {
        return singleton;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Network {
        public String id;
        public String name;
        public int typCars;
        public List<Integer> holidays;
        public int openTime;
        public int duration;
        public String timezone;
        public String newsURL;
        public List<String> lines;
        public List<String> stations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Line {
        public String id;
        public String name;
        public String color;
        public String network;
        public int typCars;
        public List<String> stations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Station {
        public String id;
        public String name;
        public String network;
        public StationFeatures features;
        public List<String> lobbies;
        public List<String> lines;
        public List<WiFiAP> wiFiAPs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class StationFeatures {
        public boolean lift;
        public boolean bus;
        public boolean boat;
        public boolean train;
        public boolean airport;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Lobby {
        public String id;
        public String name;
        public String network;
        public String station;
        public List<Exit> exits;
        public List<Schedule> schedule;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Exit {
        public int id;
        public float[] worldCoord;
        public List<String> streets;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Schedule {
        public boolean holiday;
        public int day;
        public boolean open;
        public int openTime;
        public int duration;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Connection {
        public String from;
        public String to;
        public int typS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Transfer {
        public String station;
        public String from;
        public String to;
        public int typS;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class DatasetInfo {
        public String network;
        public String version;
        public List<String> authors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class WiFiAP {
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
    }

    private int timeoutMs;
    private URI endpoint;

    private API(URI endpoint, int timeoutMs) {
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
    }

    private ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    private InputStream getRequest(URI uri) throws APIException {
        try {
            HttpURLConnection h = (HttpURLConnection) uri.toURL().openConnection();

            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            h.setRequestProperty("Accept", "application/msgpack");
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
            if (code == 200) {
                is = h.getInputStream();
            } else {
                is = h.getErrorStream();
            }
            return is;
        } catch (MalformedURLException e) {
            throw new APIException(e).addInfo("Malformed URL on GET request");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException on GET request");
        }
    }

    public List<Network> getNetworks() throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("networks")), new TypeReference<List<Network>>() {
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
        try {
            return mapper.readValue(getRequest(endpoint.resolve("networks/" + id)), Network.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Station getStation(String id) throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("stations/" + id)), Station.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public Lobby getLobby(String id) throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lobbies/" + id)), Lobby.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Connection> getConnections() throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("connections")), new TypeReference<List<Connection>>() {
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
        try {
            return mapper.readValue(getRequest(endpoint.resolve("transfers")), new TypeReference<List<Transfer>>() {
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
        try {
            return mapper.readValue(getRequest(endpoint.resolve("lines/" + id)), Line.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<DatasetInfo> getDatasetInfos() throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("datasets")), new TypeReference<List<DatasetInfo>>() {
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
        try {
            return mapper.readValue(getRequest(endpoint.resolve("datasets/" + id)), DatasetInfo.class);
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }

    public List<Disturbance> getDisturbances() throws APIException {
        try {
            return mapper.readValue(getRequest(endpoint.resolve("disturbances?omitduplicatestatus=true")), new TypeReference<List<Disturbance>>() {
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
        try {
            return mapper.readValue(getRequest(endpoint.resolve("disturbances?omitduplicatestatus=true&filter=ongoing")), new TypeReference<List<Disturbance>>() {
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
        try {
            String url = String.format("disturbances?omitduplicatestatus=true&start=%s",
                    URLEncoder.encode(
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(since)
                                    .replaceAll("(\\d\\d)(\\d\\d)$", "$1:$2"), "utf-8"));
            return mapper.readValue(getRequest(endpoint.resolve(url)), new TypeReference<List<Disturbance>>() {
            });
        } catch (JsonParseException e) {
            throw new APIException(e).addInfo("Parse exception");
        } catch (JsonMappingException e) {
            throw new APIException(e).addInfo("Mapping exception");
        } catch (IOException e) {
            throw new APIException(e).addInfo("IOException");
        }
    }
}
