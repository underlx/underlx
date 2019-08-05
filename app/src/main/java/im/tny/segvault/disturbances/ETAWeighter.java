package im.tny.segvault.disturbances;

import android.content.Context;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.s2ls.routing.ConnectionWeighter;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Transfer;

public class ETAWeighter extends ConnectionWeighter {
    private Context context;
    private Date lineStatusRead;
    private Map<String, LineStatusCache.Status> statuses;

    public ETAWeighter(Context context) {
        this.context = context;
    }

    private Map<String, LineStatusCache.Status> getLineStatuses() {
        if (lineStatusRead == null || new Date().getTime() - lineStatusRead.getTime() < TimeUnit.SECONDS.toMillis(5)) {
            statuses = Coordinator.get(context).getLineStatusCache().getLineStatus();
            lineStatusRead = new Date();
        }
        return statuses;
    }

    private boolean canUseLineStatus(LineStatusCache.Status lineStatus) {
        return lineStatus != null &&
                new Date().getTime() - lineStatus.updated.getTime() < java.util.concurrent.TimeUnit.MINUTES.toMillis(5) &&
                lineStatus.condition.trainFrequency > 0;
    }

    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        Connection.Times times = connection.getTimes();
        double weight = times.typSeconds;
        if (isSource(connection.getSource())) {
            LineStatusCache.Status lineStatus = getLineStatuses().get(connection.getSource().getLine().getId());
            if (canUseLineStatus(lineStatus)) {
                weight += (lineStatus.condition.trainFrequency / 1000.0) / 2.0; // division by two to get avg waiting time
            } else {
                weight += times.typWaitingSeconds;
            }
        } else if (connection instanceof Transfer) {
            LineStatusCache.Status lineStatus = getLineStatuses().get(connection.getTarget().getLine().getId());
            if (canUseLineStatus(lineStatus)) {
                weight = (lineStatus.condition.trainFrequency / 1000.0) / 2.0; // division by two to get avg waiting time
            }
            // else weight is typSeconds which is the crowdsourced avg waiting time for this transfer
        } else {
            weight += times.typStopSeconds;
        }
        return weight;
    }
}
