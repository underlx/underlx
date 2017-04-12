package im.tny.segvault.subway;

import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.ClassBasedEdgeFactory;

/**
 * Created by gabriel on 4/5/17.
 */

public class ConnectionFactory extends ClassBasedEdgeFactory<Station, Connection> implements EdgeFactory<Station, Connection> {
    private Network network;

    public ConnectionFactory() {
        super(Connection.class);
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public Connection createEdge(Station sourceVertex, Station targetVertex) {
        Connection c = super.createEdge(sourceVertex, targetVertex);
        //c.setNetwork(network);
        return c;
    }
}
