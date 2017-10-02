package im.tny.segvault.disturbances;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import im.tny.segvault.disturbances.exception.CacheException;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/9/17.
 */

public class TopologyCache {
    static public void saveNetwork(Context context, Network network) throws CacheException {
        String filename = "net-" + network.getId();
        try {
            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(network);
            os.close();
            fos.close();
        } catch (FileNotFoundException e) {
            throw new CacheException(e).addInfo("File " + filename + " not found");
        } catch (IOException e) {
            throw new CacheException(e).addInfo("IO Exception");
        }
    }

    static public Network loadNetwork(Context context, String id) throws CacheException {
        String filename = "net-" + id;

        Network network = null;
        try {
            FileInputStream fis = context.openFileInput(filename);
            ObjectInputStream is = new ObjectInputStream(fis);
            network = (Network) is.readObject();
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
        return network;
    }
}
