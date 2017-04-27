package im.tny.segvault.subway;

/**
 * Created by gabriel on 4/27/17.
 */

public interface IEdgeWeighter {
    public double getEdgeWeight(Network network, Connection connection);
}
