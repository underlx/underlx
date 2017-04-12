package im.tny.segvault.disturbances.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 4/9/17.
 */

public class APIException extends Exception {
    public APIException(Throwable cause) {
        super(cause);
    }

    protected List<String> info = new ArrayList<String>();

    public APIException addInfo(String context) {
        info.add(context);
        return this;
    }

}
