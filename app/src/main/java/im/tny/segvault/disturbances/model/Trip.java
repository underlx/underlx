package im.tny.segvault.disturbances.model;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by gabriel on 5/8/17.
 */

public class Trip extends RealmObject {
    @PrimaryKey
    private String id;

    private RealmList<StationUse> path;

    private boolean synced;

    private boolean userConfirmed;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RealmList<StationUse> getPath() {
        return path;
    }

    public void setPath(RealmList<StationUse> path) {
        this.path = path;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    public void setUserConfirmed(boolean userConfirmed) {
        this.userConfirmed = userConfirmed;
    }

    public Path toConnectionPath(Network network) {
        List<Connection> edges = new LinkedList<>();
        List<Pair<Date, Date>> times = new ArrayList<>();
        List<Boolean> manualEntry = new ArrayList<>();
        Stop startVertex = null;

        List<Stop> previous = new ArrayList<>();
        for (StationUse use : path) {
            switch (use.getType()) {
                case INTERCHANGE:
                    Stop source = null;
                    Stop target = null;
                    for (Stop s : network.getStation(use.getStation().getId()).getStops()) {
                        if (s.getLine().getId().equals(use.getSourceLine())) {
                            source = s;
                        }
                        if (s.getLine().getId().equals(use.getTargetLine())) {
                            target = s;
                        }
                    }
                    for (Stop s : previous) {
                        Connection e = network.getEdge(s, source);
                        if (e != null) {
                            edges.add(e);
                        }
                    }
                    Connection edge = network.getEdge(source, target);
                    if (edge != null) {
                        edges.add(edge);
                        previous = new ArrayList<>();
                        previous.add(target);
                        // in a Path, there are originally two entries for an interchange
                        times.add(new Pair<>(use.getEntryDate(), use.getLeaveDate()));
                        times.add(new Pair<>(use.getEntryDate(), use.getLeaveDate()));
                        manualEntry.add(use.isManualEntry());
                        manualEntry.add(use.isManualEntry());
                    }
                    break;
                case NETWORK_ENTRY:
                    previous = new ArrayList<>(network.getStation(use.getStation().getId()).getStops());
                    times.add(new Pair<>(use.getEntryDate(), use.getLeaveDate()));
                    manualEntry.add(use.isManualEntry());
                    break;
                case NETWORK_EXIT:
                case GONE_THROUGH:
                    for (Stop s : previous) {
                        for (Stop s2 : network.getStation(use.getStation().getId()).getStops()) {
                            Connection e = network.getEdge(s, s2);
                            // deal with our way of closing stations
                            // where closed stations only have their outbound edges
                            // (but the user may have travelled through a no longer existing
                            // inbound edge, before the station closed)
                            if (e == null && network.getEdge(s2, s) != null) {
                                e = new Connection(true, s, s2);
                            }
                            if (e != null) {
                                edges.add(e);
                                previous = new ArrayList<>();
                                previous.add(s2);
                                times.add(new Pair<>(use.getEntryDate(), use.getLeaveDate()));
                                manualEntry.add(use.isManualEntry());
                            }
                        }
                    }
                    break;
                case VISIT:
                    times.add(new Pair<>(use.getEntryDate(), use.getLeaveDate()));
                    manualEntry.add(use.isManualEntry());
                    // a stop, any stop
                    startVertex = network.getStation(use.getStation().getId()).getStops().iterator().next();
                    break;
            }
        }
        if(startVertex == null) {
            startVertex = edges.get(0).getSource();
        }
        return new Path(network, startVertex, edges, times, manualEntry, 0);
    }


    public static String persistConnectionPath(Path path) {
        return persistConnectionPath(path, null);
    }

    public static String persistConnectionPath(Path path, String replaceTrip) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        RealmList<StationUse> uses = new RealmList<>();
        int size = path.getEdgeList().size();
        if (size == 0) {
            StationUse use = new StationUse();
            use.setType(StationUse.UseType.VISIT);
            use.setStation(realm.where(RStation.class).equalTo("id", path.getStartVertex().getStation().getId()).findFirst());
            use.setEntryDate(path.getEntryExitTimes(0).first);
            use.setLeaveDate(path.getEntryExitTimes(0).second);
            use.setManualEntry(path.getManualEntry(0));
            uses.add(realm.copyToRealm(use));
        } else if (size == 1 && path.getEdgeList().get(0) instanceof Transfer) {
            Connection c = path.getEdgeList().get(0);
            StationUse use = new StationUse();
            use.setType(StationUse.UseType.VISIT);
            use.setSourceLine(c.getSource().getLine().getId());
            use.setTargetLine(c.getTarget().getLine().getId());
            use.setEntryDate(path.getEntryExitTimes(0).first);
            use.setLeaveDate(path.getEntryExitTimes(0).second);
            use.setManualEntry(path.getManualEntry(0));
            use.setStation(realm.where(RStation.class).equalTo("id", c.getSource().getStation().getId()).findFirst());
            uses.add(realm.copyToRealm(use));
        } else {
            int timeIdx = 0;
            StationUse startUse = new StationUse();
            startUse.setType(StationUse.UseType.NETWORK_ENTRY);
            startUse.setStation(realm.where(RStation.class).equalTo("id", path.getStartVertex().getStation().getId()).findFirst());
            startUse.setEntryDate(path.getEntryExitTimes(timeIdx).first);
            startUse.setLeaveDate(path.getEntryExitTimes(timeIdx).second);
            startUse.setManualEntry(path.getManualEntry(timeIdx));
            uses.add(realm.copyToRealm(startUse));

            int i = 1;
            timeIdx++;
            if (path.getEdgeList().get(0) instanceof Transfer) {
                i = 2;
                timeIdx++;
            }

            for (; i < size; i++) {
                Connection c = path.getEdgeList().get(i);
                StationUse use = new StationUse();
                if (c instanceof Transfer) {
                    if (i == size - 1) break;
                    use.setType(StationUse.UseType.INTERCHANGE);
                    use.setSourceLine(c.getSource().getLine().getId());
                    use.setTargetLine(c.getTarget().getLine().getId());
                    use.setEntryDate(path.getEntryExitTimes(timeIdx).first);
                    timeIdx++;
                    use.setLeaveDate(path.getEntryExitTimes(timeIdx).second);
                    use.setManualEntry(path.getManualEntry(timeIdx));
                    timeIdx++;
                    i++; // skip next station as then we'd have duplicate uses for the same station ID
                } else {
                    use.setType(StationUse.UseType.GONE_THROUGH);
                    use.setEntryDate(path.getEntryExitTimes(timeIdx).first);
                    use.setLeaveDate(path.getEntryExitTimes(timeIdx).second);
                    use.setManualEntry(path.getManualEntry(timeIdx));
                    timeIdx++;
                }
                use.setStation(realm.where(RStation.class).equalTo("id", c.getSource().getStation().getId()).findFirst());
                uses.add(realm.copyToRealm(use));
            }

            StationUse endUse = new StationUse();
            endUse.setType(StationUse.UseType.NETWORK_EXIT);
            endUse.setStation(realm.where(RStation.class).equalTo("id", path.getEndVertex().getStation().getId()).findFirst());
            endUse.setEntryDate(path.getEntryExitTimes(timeIdx).first);
            endUse.setLeaveDate(path.getEntryExitTimes(timeIdx).second);
            endUse.setManualEntry(path.getManualEntry(timeIdx));
            uses.add(realm.copyToRealm(endUse));
        }

        Trip trip;
        if (replaceTrip != null) {
            trip = realm.where(Trip.class).equalTo("id", replaceTrip).findFirst();
            trip.getPath().deleteAllFromRealm();
            trip.setUserConfirmed(true);
        } else {
            trip = realm.createObject(Trip.class, UUID.randomUUID().toString());
            trip.setUserConfirmed(false);
        }
        trip.setPath(uses);
        realm.commitTransaction();
        realm.close();
        return trip.getId();
    }

    public boolean missingCorrection() {
        return !userConfirmed && canBeCorrected();
    }

    public boolean canBeCorrected() {
        return new Date().getTime() - path.first().getEntryDate().getTime() < TimeUnit.DAYS.toMillis(7);
    }
}
