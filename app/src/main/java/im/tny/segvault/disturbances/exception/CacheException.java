package im.tny.segvault.disturbances.exception;

import android.support.constraint.solver.Cache;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 4/9/17.
 */

public class CacheException extends Exception{
    public CacheException(Throwable cause) {
        super(cause);
    }

    protected List<String> info = new ArrayList<String>();

    public CacheException addInfo(String context) {
        info.add(context);
        return this;
    }
}
