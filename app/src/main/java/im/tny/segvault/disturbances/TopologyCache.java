package im.tny.segvault.disturbances;

import android.content.Context;
import android.graphics.Color;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.tny.segvault.disturbances.exception.CacheException;
import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 4/9/17.
 */

public class TopologyCache {
    static private class Topology implements Serializable {
        public API.Network network;
        public HashMap<String, API.Station> stations;
        public HashMap<String, API.Lobby> lobbies;
        public HashMap<String, API.Line> lines;
        public List<API.Connection> connections;
        public List<API.Transfer> transfers;
        public API.DatasetInfo info;
    }

    static public void saveNetwork(Context context,
                                      API.Network network,
                                      HashMap<String, API.Station> stations,
                                      HashMap<String, API.Lobby> lobbies,
                                      HashMap<String, API.Line> lines,
                                      List<API.Connection> connections,
                                      List<API.Transfer> transfers,
                                      API.DatasetInfo info) throws CacheException {
        Topology t = new Topology();
        t.network = network;
        t.stations = stations;
        t.lobbies = lobbies;
        t.lines = lines;
        t.connections = connections;
        t.transfers = transfers;
        t.info = info;

        String filename = "net-" + network.id;
        try {
            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(t);
            os.close();
            fos.close();
        } catch (FileNotFoundException e) {
            throw new CacheException(e).addInfo("File " + filename + " not found");
        } catch (IOException e) {
            throw new CacheException(e).addInfo("IO Exception");
        }
    }

    static public Network loadNetwork(Context context, String id, String apiEndpoint) throws CacheException {
        String filename = "net-" + id;

        Topology t = null;
        try {
            FileInputStream fis = context.openFileInput(filename);
            ObjectInputStream is = new ObjectInputStream(fis);
            t = (Topology) is.readObject();
            is.close();
            fis.close();
        } catch (FileNotFoundException e) {
            throw new CacheException(e).addInfo("File " + filename + " not found");
        } catch (IOException e) {
            throw new CacheException(e).addInfo("IO exception");
        } catch (ClassNotFoundException e) {
            throw new CacheException(e).addInfo("Class not found");
        } catch (Exception e) {
            e.printStackTrace();
            throw new CacheException(e).addInfo("Unforeseen exception");
        }
        Network net = new Network(t.network.id, t.network.name, t.network.typCars,
                t.network.holidays, t.network.openTime * 1000, t.network.duration * 1000,
                t.network.timezone, t.network.newsURL);

        for (String lineid : t.network.lines) {
            API.Line l = t.lines.get(lineid);
            Line line = new Line(net, new HashSet<Stop>(), l.id, l.name, l.typCars);
            line.setColor(Color.parseColor("#" + l.color));
            boolean isFirstStationInLine = true;
            for (String sid : l.stations) {
                API.Station s = t.stations.get(sid);
                Station station = net.getStation(s.id);
                if (station == null) {
                    Map<String, String> triviaURLs = new HashMap<>();
                    for (Map.Entry<String, String> entry : s.triviaURLs.entrySet()) {
                        triviaURLs.put(entry.getKey(), apiEndpoint + entry.getValue());
                    }

                    Map<String, Map<String, String>> connURLs = new HashMap<>();
                    for (Map.Entry<String, Map<String, String>> entry : s.connURLs.entrySet()) {
                        Map<String, String> urls = new HashMap<>();
                        for (Map.Entry<String, String> localeEntry : entry.getValue().entrySet()) {
                            urls.put(localeEntry.getKey(), apiEndpoint + localeEntry.getValue());
                        }
                        connURLs.put(entry.getKey(), urls);
                    }

                    station = new Station(net, s.id, s.name, s.altNames,
                            new Station.Features(s.features.lift, s.features.bus, s.features.boat, s.features.train, s.features.airport),
                            triviaURLs);
                    station.setConnectionURLs(connURLs);

                    // Lobbies
                    for (String lid : s.lobbies) {
                        API.Lobby alobby = t.lobbies.get(lid);
                        Lobby lobby = new Lobby(alobby.id, alobby.name);
                        for (API.Exit aexit : alobby.exits) {
                            Lobby.Exit exit = new Lobby.Exit(aexit.id, aexit.worldCoord, aexit.streets);
                            lobby.addExit(exit);
                        }
                        for (API.Schedule asched : alobby.schedule) {
                            Lobby.Schedule sched = new Lobby.Schedule(
                                    asched.holiday, asched.day, asched.open, asched.openTime * 1000, asched.duration * 1000);
                            lobby.addSchedule(sched);
                        }
                        station.addLobby(lobby);
                    }
                }
                Stop stop = new Stop(station, line);
                line.addVertex(stop);
                station.addVertex(stop);
                if (isFirstStationInLine) {
                    line.setFirstStop(stop);
                    isFirstStationInLine = false;
                }

                // WiFi APs
                for (API.WiFiAP w : s.wiFiAPs) {
                    // take line affinity into account
                    if (w.line.equals(line.getId())) {
                        WiFiLocator.addBSSIDforStop(stop, new BSSID(w.bssid));
                    }
                }
            }
            net.addLine(line);
        }

        // Connections are within stations in the same line
        for (API.Connection c : t.connections) {
            Set<Stop> sFrom = net.getStation(c.from).vertexSet();
            Set<Stop> sTo = net.getStation(c.to).vertexSet();
            Stop from = null, to = null;
            for (Stop s : sFrom) {
                for (Stop s2 : sTo) {
                    if (s.getLine().getId().equals(s2.getLine().getId())) {
                        from = s;
                        to = s2;
                    }
                }
            }
            if (from != null && to != null) {
                Connection newConnection = net.addEdge(from, to);
                from.getLine().addEdge(from, to);
                newConnection.setTimes(new Connection.Times(c.typWaitS, c.typStopS, c.typS));
                newConnection.setWorldLength(c.worldLength);
                net.setEdgeWeight(newConnection, c.typS);
            }
        }

        for (API.Transfer tr : t.transfers) {
            Transfer newTransfer = new Transfer();
            // find stations with the right IDs for each line
            Station station = net.getStation(tr.station);
            for (Stop from : station.vertexSet()) {
                for (Stop to : station.vertexSet()) {
                    if (from.getLine().getId().equals(tr.from) && to.getLine().getId().equals(tr.to)) {
                        net.addEdge(from, to, newTransfer);
                        net.setEdgeWeight(newTransfer, tr.typS);
                        newTransfer.setTimes(new Connection.Times(0, 0, tr.typS));
                    }
                }

            }
        }

        net.setDatasetAuthors(t.info.authors);
        net.setDatasetVersion(t.info.version);
        return net;
    }
}
