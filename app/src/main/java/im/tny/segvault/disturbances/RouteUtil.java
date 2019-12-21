package im.tny.segvault.disturbances;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.StationUse;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.routing.ChangeLineStep;
import im.tny.segvault.s2ls.routing.EnterStep;
import im.tny.segvault.s2ls.routing.ExitStep;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.s2ls.routing.Step;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Stop;

public class RouteUtil {
    public static Stop getLikelyNextExit(Context context, List<Connection> path, double threshold) {
        if (path.size() == 0) {
            return null;
        }
        // get the line for the latest connection
        Connection last = path.get(path.size() - 1);
        Line line = null;
        for (Line l : Coordinator.get(context).getMapManager().getAllLines()) {
            if (l.edgeSet().contains(last)) {
                line = l;
                break;
            }
        }
        if (line == null) {
            return null;
        }

        Set<Stop> alreadyVisited = new HashSet<>();
        for (Connection c : path) {
            alreadyVisited.add(c.getSource());
            alreadyVisited.add(c.getTarget());
        }

        // get all the stops till the end of the line, after the given connection
        // (or in the case of circular lines, all stops of the line)
        Stop maxStop = null;
        double max = 0;
        Set<Stop> stops = new HashSet<>();
        while (stops.add(last.getSource())) {
            Stop curStop = last.getTarget();
            if (!alreadyVisited.contains(curStop)) {
                double r = getLeaveTrainFactorForStop(context, curStop);
                if (maxStop == null || r > max) {
                    maxStop = curStop;
                    max = r;
                }
            }
            if (line.outDegreeOf(curStop) == 1) {
                break;
            }
            for (Connection outedge : line.outgoingEdgesOf(curStop)) {
                if (!stops.contains(outedge.getTarget())) {
                    last = outedge;
                    break;
                }
            }
        }

        if (max < threshold) {
            // most relevant station is not relevant enough
            return null;
        }
        return maxStop;
    }

    public static double getLeaveTrainFactorForStop(Context context, Stop stop) {
        AppDatabase db = Coordinator.get(context).getDB();
        long entryCount = db.stationUseDao().countStationUsesOfType(stop.getStation().getId(), StationUse.UseType.NETWORK_ENTRY);
        long exitCount = db.stationUseDao().countStationUsesOfType(stop.getStation().getId(), StationUse.UseType.NETWORK_EXIT);
        // number of times user left at this stop to transfer to another line
        long transferCount = db.stationUseDao().countStationInterchangesToLine(stop.getStation().getId(), stop.getLine().getId());
        return entryCount * 0.3 + exitCount + transferCount;
    }

    public static class RouteStepInfo {
        public CharSequence title;
        public CharSequence summary;
        public List<CharSequence> bodyLines = new ArrayList<>();
        public int color;
    }

    public static RouteStepInfo buildRouteStepInfo(Context context, Route currentRoute, Path currentPath) {
        RouteStepInfo info = new RouteStepInfo();

        info.summary = String.format(context.getString(R.string.notif_route_navigating_status), currentRoute.getTarget().getName());
        Step nextStep = currentRoute.getNextStep(currentPath);
        if (nextStep instanceof EnterStep) {
            if (currentPath != null && currentPath.getCurrentStop() != null && currentRoute.checkPathStartsRoute(currentPath)) {
                info.title = String.format(context.getString(R.string.notif_route_catch_train_title), ((EnterStep) nextStep).getDirection().getName(12));
            } else {
                info.title = String.format(context.getString(R.string.notif_route_enter_station_title), nextStep.getStation().getName(15));
            }
            // TODO: show "encurtamentos" warnings here if applicable
            info.bodyLines.add(String.format(context.getString(R.string.notif_route_catch_train_status), ((EnterStep) nextStep).getDirection().getName()));
            info.color = currentRoute.getSourceStop().getLine().getColor();
        } else if (nextStep instanceof ChangeLineStep) {
            ChangeLineStep clStep = (ChangeLineStep) nextStep;
            String lineName = Util.getLineNames(context, clStep.getTarget())[0];
            String titleStr;
            if (currentPath != null && currentPath.getCurrentStop() != null && currentPath.getCurrentStop().getStation() == nextStep.getStation()) {
                titleStr = String.format(context.getString(R.string.notif_route_catch_train_line_change_title),
                        lineName);
            } else {
                titleStr = String.format(context.getString(R.string.notif_route_line_change_title),
                        nextStep.getStation().getName(10),
                        lineName);
            }
            int lStart = titleStr.indexOf(lineName);
            int lEnd = lStart + lineName.length();
            Spannable sb = new SpannableString(titleStr);
            sb.setSpan(new ForegroundColorSpan(clStep.getTarget().getColor()), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            info.title = sb;

            // TODO: show "encurtamentos" warnings here if applicable
            sb = new SpannableString(
                    String.format(context.getString(R.string.notif_route_catch_train_status),
                            clStep.getDirection().getName())
            );
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            info.bodyLines.add(sb);
            info.color = clStep.getTarget().getColor();
        } else if (nextStep instanceof ExitStep) {
            if (currentPath != null && currentPath.getCurrentStop().getStation() == nextStep.getStation()) {
                info.title = context.getString(R.string.notif_route_leave_train_now);
            } else if (currentPath != null && currentPath.getNextStop() != null &&
                    new Date().getTime() - currentPath.getCurrentStopEntryTime().getTime() > 30 * 1000 &&
                    nextStep.getStation() == currentPath.getNextStop().getStation()) {
                info.title = context.getString(R.string.notif_route_leave_train_next);
            } else {
                info.title = String.format(context.getString(R.string.notif_route_leave_train), nextStep.getStation().getName(20));
            }
            info.color = nextStep.getLine().getColor();
        }

        return info;
    }
}
