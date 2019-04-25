package im.tny.segvault.disturbances;

import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.FutureConnection;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

import java.util.concurrent.TimeUnit;

import static org.fusesource.hawtbuf.Buffer.utf8;

public class BlockingConnectionWithTimeouts {

    private final FutureConnection next;
    private final BlockingConnection blocking;

    public BlockingConnectionWithTimeouts(FutureConnection next) {
        this.next = next;
        this.blocking = new BlockingConnection(next);
    }

    public boolean isConnected() {
        return next.isConnected();
    }

    public void connect(long amount, TimeUnit unit) throws Exception {
        this.next.connect().await(amount, unit);
    }

    public void disconnect(long amount, TimeUnit unit) throws Exception {
        this.next.disconnect().await(amount, unit);
    }

    public void kill(long amount, TimeUnit unit) throws Exception {
        this.next.kill().await(amount, unit);
    }

    public byte[] subscribe(final Topic[] topics, long amount, TimeUnit unit) throws Exception {
        return this.next.subscribe(topics).await(amount, unit);
    }

    public void unsubscribe(final String[] topics, long amount, TimeUnit unit) throws Exception {
        this.next.unsubscribe(topics).await(amount, unit);
    }

    public void unsubscribe(final UTF8Buffer[] topics, long amount, TimeUnit unit) throws Exception {
        this.next.unsubscribe(topics).await(amount, unit);
    }

    public void publish(final UTF8Buffer topic, final Buffer payload, final QoS qos, final boolean retain, long amount, TimeUnit unit) throws Exception {
        this.next.publish(topic, payload, qos, retain).await(amount, unit);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public void publish(final String topic, final byte[] payload, final QoS qos, final boolean retain, long amount, TimeUnit unit) throws Exception {
        publish(utf8(topic), new Buffer(payload), qos, retain, amount, unit);
    }

    public Message receive() throws Exception {
        return this.next.receive().await();
    }

    /**
     * @return null if the receive times out.
     */
    public Message receive(long amount, TimeUnit unit) throws Exception {
        return blocking.receive(amount, unit);
    }

    public void setReceiveBuffer(final long receiveBuffer) throws InterruptedException {
        blocking.setReceiveBuffer(receiveBuffer);
    }

    public long getReceiveBuffer() throws InterruptedException {
        return blocking.getReceiveBuffer();
    }

    public void resume() {
        next.resume();
    }

    public void suspend() {
        next.suspend();
    }
}
