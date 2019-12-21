package im.tny.segvault.disturbances.database;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

@Entity(tableName = "trip")
public class Trip {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "synced")
    public boolean synced;

    @ColumnInfo(name = "user_confirmed")
    public boolean userConfirmed;

    @ColumnInfo(name = "submitted")
    public boolean submitted;

    @ColumnInfo(name = "sync_failures")
    public int syncFailures = 0;

    public List<StationUse> getPath(AppDatabase db) {
        return db.stationUseDao().getOfTrip(id);
    }

    public Path toConnectionPath(AppDatabase db, Network network) {
        List<StationUse> path = db.stationUseDao().getOfTrip(id);
        return toConnectionPath(path, network);
    }

    public Path toConnectionPath(List<StationUse> path, Network network) {
        List<Connection> edges = new LinkedList<>();
        List<Pair<Date, Date>> times = new ArrayList<>();
        List<Boolean> manualEntry = new ArrayList<>();
        Stop startVertex = null;

        List<Stop> previous = new ArrayList<>();
        for (StationUse use : path) {
            switch (use.type) {
                case INTERCHANGE:
                    Stop source = null;
                    Stop target = null;
                    for (Stop s : network.getStation(use.stationID).getStops()) {
                        if (s.getLine().getId().equals(use.sourceLine)) {
                            source = s;
                        }
                        if (s.getLine().getId().equals(use.targetLine)) {
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
                        times.add(new Pair<>(use.entryDate, use.leaveDate));
                        times.add(new Pair<>(use.entryDate, use.leaveDate));
                        manualEntry.add(use.manualEntry);
                        manualEntry.add(use.manualEntry);
                    }
                    break;
                case NETWORK_ENTRY:
                    previous = new ArrayList<>(network.getStation(use.stationID).getStops());
                    times.add(new Pair<>(use.entryDate, use.leaveDate));
                    manualEntry.add(use.manualEntry);
                    break;
                case NETWORK_EXIT:
                case GONE_THROUGH:
                    for (Stop s : previous) {
                        for (Stop s2 : network.getStation(use.stationID).getStops()) {
                            Connection e = network.getEdge(s, s2);
                            // deal with our way of closing stations
                            // where closed stations only have their outbound edges
                            // (but the user may have travelled through a no longer existing
                            // inbound edge, before the station closed)
                            Connection otherDir = network.getEdge(s2, s);
                            if (e == null && otherDir != null) {
                                e = new Connection(true, s, s2, otherDir.getTimes(), otherDir.getWorldLength());
                            }
                            if (e != null) {
                                edges.add(e);
                                previous = new ArrayList<>();
                                previous.add(s2);
                                times.add(new Pair<>(use.entryDate, use.leaveDate));
                                manualEntry.add(use.manualEntry);
                            }
                        }
                    }
                    break;
                case VISIT:
                    times.add(new Pair<>(use.entryDate, use.leaveDate));
                    manualEntry.add(use.manualEntry);
                    // a stop, any stop
                    startVertex = network.getStation(use.stationID).getStops().iterator().next();
                    break;
            }
        }
        if (startVertex == null) {
            if (edges.size() > 0) {
                startVertex = edges.get(0).getSource();
            } else if (path.size() > 0) {
                // handling a situation that should not occur
                // this DB entry is definitely corrupt
                startVertex = network.getStation(path.get(0).stationID).getStops().iterator().next();
            } else {
                // handling a situation that should not occur
                // this DB entry is definitely corrupt
                return null;
            }
        }
        return new Path(network, startVertex, edges, times, manualEntry, 0);
    }

    @Nullable
    public static String persistConnectionPath(AppDatabase db, Path path) {
        return persistConnectionPath(db, path, null);
    }

    @Nullable
    public static String persistConnectionPath(AppDatabase db, Path path, String replaceTrip) {
        List<StationUse> uses = new ArrayList<>();

        List<Connection> edgeList = path.getEdgeList();
        int size = edgeList.size();
        if (size == 0) {
            StationUse use = new StationUse();
            use.order = uses.size();
            use.type = StationUse.UseType.VISIT;
            use.stationID = path.getStartVertex().getStation().getId();
            use.entryDate = path.getEntryExitTimes(0).first;
            use.leaveDate = path.getEntryExitTimes(0).second;
            use.manualEntry = path.getManualEntry(0);
            uses.add(use);
        } else if (size == 1 && edgeList.get(0) instanceof Transfer) {
            Connection c = edgeList.get(0);
            StationUse use = new StationUse();
            use.order = uses.size();
            use.type = StationUse.UseType.VISIT;
            use.sourceLine = c.getSource().getLine().getId();
            use.targetLine = c.getTarget().getLine().getId();
            use.entryDate = path.getEntryExitTimes(0).first;
            use.leaveDate = path.getEntryExitTimes(0).second;
            use.manualEntry = path.getManualEntry(0);
            use.stationID = c.getSource().getStation().getId();
            uses.add(use);
        } else {
            int timeIdx = 0;
            StationUse startUse = new StationUse();
            startUse.order = uses.size();
            startUse.type = StationUse.UseType.NETWORK_ENTRY;
            startUse.stationID = path.getStartVertex().getStation().getId();
            startUse.entryDate = path.getEntryExitTimes(timeIdx).first;
            startUse.leaveDate = path.getEntryExitTimes(timeIdx).second;
            startUse.manualEntry = path.getManualEntry(timeIdx);
            uses.add(startUse);

            int i = 1;
            timeIdx++;
            if (edgeList.get(0) instanceof Transfer) {
                i = 2;
                timeIdx++;
            }

            for (; i < size; i++) {
                Connection c = edgeList.get(i);
                StationUse use = new StationUse();
                use.order = uses.size();
                if (c instanceof Transfer) {
                    if (i == size - 1) break;
                    use.type = StationUse.UseType.INTERCHANGE;
                    use.sourceLine = c.getSource().getLine().getId();
                    use.targetLine = c.getTarget().getLine().getId();
                    use.entryDate = path.getEntryExitTimes(timeIdx).first;
                    timeIdx++;
                    use.leaveDate = path.getEntryExitTimes(timeIdx).second;
                    use.manualEntry = path.getManualEntry(timeIdx);
                    timeIdx++;
                    i++; // skip next station as then we'd have duplicate uses for the same station ID
                } else {
                    use.type = StationUse.UseType.GONE_THROUGH;
                    use.entryDate = path.getEntryExitTimes(timeIdx).first;
                    use.leaveDate = path.getEntryExitTimes(timeIdx).second;
                    use.manualEntry = path.getManualEntry(timeIdx);
                    timeIdx++;
                }
                use.stationID = c.getSource().getStation().getId();
                uses.add(use);
            }

            StationUse endUse = new StationUse();
            endUse.order = uses.size();
            endUse.type = StationUse.UseType.NETWORK_EXIT;
            endUse.stationID = path.getEndVertex().getStation().getId();
            endUse.entryDate = path.getEntryExitTimes(timeIdx).first;
            endUse.leaveDate = path.getEntryExitTimes(timeIdx).second;
            endUse.manualEntry = path.getManualEntry(timeIdx);
            uses.add(endUse);
        }

        AtomicReference<String> tripId = new AtomicReference<>();
        tripId.set(null);
        db.runInTransaction(() -> {
            Trip trip;
            if (replaceTrip != null) {
                trip = db.tripDao().get(replaceTrip);
                db.stationUseDao().deleteOfTrip(replaceTrip);
                trip.userConfirmed = true;
                trip.synced = false;
                db.tripDao().updateAll(trip);
            } else {
                // intervals overlap if (StartA <= EndB) and (EndA >= StartB)
                // our A is the station use in the DB we want to check
                // our B is this whole trip
                Date startB = uses.get(0).entryDate;
                Date endB = uses.get(uses.size() - 1).leaveDate;
                boolean hasOverlap = db.stationUseDao().countOverlapping(startB, endB) > 0;
                if (hasOverlap) {
                    // this trip overlaps with a previous trip. this should never happen, but if it does, we'll prevent shenanigans here
                    return;
                }
                trip = new Trip();
                trip.id = UUID.randomUUID().toString();
                trip.userConfirmed = false;
                trip.synced = false;
                db.tripDao().insertAll(trip);
            }

            // insert uses after trip, as uses have a FK to trip
            for (StationUse use : uses) {
                use.tripID = trip.id;
                db.stationUseDao().insertAll(use);
            }
            tripId.set(trip.id);
        });
        return tripId.get();
    }

    public static void confirm(AppDatabase db, final String id) {
        Trip trip = db.tripDao().get(id);
        trip.userConfirmed = true;
        trip.synced = false;
        db.tripDao().updateAll(trip);
    }

    public boolean canBeCorrected(AppDatabase db) {
        List<StationUse> path = getPath(db);
        return new Date().getTime() - path.get(0).entryDate.getTime() < TimeUnit.DAYS.toMillis(7);
    }

    public static Date getConfirmCutoff() {
        return new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(7));
    }
}
