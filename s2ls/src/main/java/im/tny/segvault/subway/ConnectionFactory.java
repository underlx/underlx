package im.tny.segvault.subway;

import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.ClassBasedEdgeFactory;

/**
 * Created by gabriel on 4/5/17.
 */

public class ConnectionFactory extends ClassBasedEdgeFactory<Stop, Connection> implements EdgeFactory<Stop, Connection> {
    private Network network;

    public ConnectionFactory() {
        super(Connection.class);
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public Connection createEdge(Stop sourceVertex, Stop targetVertex) {
        Connection c = super.createEdge(sourceVertex, targetVertex);
        //c.setNetwork(network);
        return c;
    }
}
