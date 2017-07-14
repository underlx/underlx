package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.UUID;

import im.tny.segvault.disturbances.exception.APIException;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by gabriel on 7/14/17.
 */

public class PairManager {
    private Context context;
    private SharedPreferences sharedPref;
    private final static String PAIR_PREFERENCES = "pairsettings";
    private final static String PREF_API_KEY = "pref_api_key";
    private final static String PREF_API_SECRET = "pref_api_secret";
    private final static String PREF_API_ACTIVATION = "pref_api_activation";

    public PairManager(Context context) {
        this.context = context;
        sharedPref = context.getSharedPreferences(PAIR_PREFERENCES, MODE_PRIVATE);
    }

    public boolean isPaired() {
        return sharedPref.getString(PREF_API_KEY, "").length() > 0;
    }

    public boolean isActivated() {
        return sharedPref.getLong(PREF_API_ACTIVATION, Long.MAX_VALUE) < new Date().getTime();
    }

    public void pair() {
        if (isPaired()) {
            return;
        }

        API.PairRequest request = new API.PairRequest();
        request.nonce = UUID.randomUUID().toString();
        request.timestamp = Util.encodeRFC3339(new Date());
        request.androidID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        String toSign = request.nonce + request.timestamp + request.androidID;

        byte[] sigBytes;
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(Util.getPrivateKeyFromAsset(context, "trusted.der"));
            byte[] strByte = toSign.getBytes("UTF-8");
            signer.update(strByte);
            sigBytes = signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException | SignatureException e) {
            e.printStackTrace();
            return;
        }

        if (sigBytes == null) {
            return;
        }

        byte[] asn1sigBytes = new BigInteger(1, sigBytes).toByteArray();
        request.signature = Base64.encodeToString(asn1sigBytes, Base64.NO_WRAP);

        try {
            API.Pair response = API.getInstance().postPairRequest(request);

            SharedPreferences.Editor editor = sharedPref.edit();
            Log.d("PM API KEY", response.key);
            Log.d("PM API SEC", response.secret);
            Log.d("PM API ACT", String.valueOf(response.activation[0]));
            editor.putString(PREF_API_KEY, response.key);
            editor.putString(PREF_API_SECRET, response.secret);
            editor.putLong(PREF_API_ACTIVATION, response.activation[0] * 1000);
            editor.commit();
        } catch (APIException e) {
            e.printStackTrace();
        }
    }

    public void pairAsync() {
        new PairTask().execute();
    }

    public void unpair() {
        SharedPreferences sharedPref = context.getSharedPreferences(PAIR_PREFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREF_API_KEY, "");
        editor.putString(PREF_API_SECRET, "");
        editor.putLong(PREF_API_ACTIVATION, Long.MAX_VALUE);
        editor.commit();
    }

    private class PairTask extends AsyncTask<Void, Integer, Object> {
        @Override
        protected Object doInBackground(Void... strings) {
            pair();
            return new Object();
        }
    }
}
