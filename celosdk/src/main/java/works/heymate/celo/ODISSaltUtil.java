package works.heymate.celo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.celo.BlindThresholdBlsModule;
import org.celo.contractkit.ContractKit;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.net.HttpURLConnection;
import java.net.URL;

public class ODISSaltUtil {

    private static final String TAG = "ODISSalt";

    private static final String SIGN_MESSAGE_ENDPOINT = "/getBlindedMessageSig";

    private static final String AUTHENTICATION_METHOD_WALLET_KEY = "wallet_key";
    private static final String AUTHENTICATION_METHOD_ENCRYPTION_KEY = "encryption_key";
    private static final String AUTHENTICATION_METHOD_CUSTOM_SIGNER = "custom_signer";

    private static final String ERROR_ODIS_QUOTA = "odisQuotaError";
    private static final String ERROR_ODIS_INPUT = "odisBadInputError";
    private static final String ERROR_ODIS_AUTH = "odisAuthError";
    private static final String ERROR_ODIS_CLIENT = "Unknown Client Error";
    private static final String[] ERRORS = {
            ERROR_ODIS_QUOTA, ERROR_ODIS_INPUT, ERROR_ODIS_AUTH, ERROR_ODIS_CLIENT
    };

    private static final int PEPPER_CHAR_LENGTH = 13;

    // https://github.com/celo-org/celo-monorepo/blob/79d0efaf50e99ff66984269d5675e4abb0e6b46f/packages/sdk/identity/src/odis/phone-number-identifier.ts#L36
    public static String getSalt(Context context, ContractKit contractKit, String odisUrl, String odisPubKey, String target) throws CeloException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);

        if (sharedPreferences.contains(target)) {
            return sharedPreferences.getString(target, null);
        }

        String address = contractKit.getAddress();

        BlindThresholdBlsModule blsBlindingClient = new BlindThresholdBlsModule();

        String base64BlindedMessage;

        try {
            base64BlindedMessage = blsBlindingClient.blindMessage(Base64.encodeToString(target.getBytes(), Base64.DEFAULT));
        } catch (Exception e) {
            throw new CeloException(CeloError.BLINDING_ERROR, e);
        }

        JSONObject signMessageRequest = new JSONObject();

        try {
            signMessageRequest.put("account", address);
            signMessageRequest.put("timestamp", System.currentTimeMillis());
            signMessageRequest.put("blindedQueryPhoneNumber", base64BlindedMessage);
            signMessageRequest.put("authenticationMethod", AUTHENTICATION_METHOD_WALLET_KEY);
        } catch (JSONException e) { }

        // https://github.com/celo-org/celo-monorepo/blob/79d0efaf50e99ff66984269d5675e4abb0e6b46f/packages/sdk/identity/src/odis/query.ts#L116
        String bodyString = signMessageRequest.toString();

        Sign.SignatureData signatureData = Sign.signPrefixedMessage(bodyString.getBytes(), contractKit.transactionManager.getCredentials().getEcKeyPair());
        // https://github.com/celo-org/celo-monorepo/blob/79d0efaf50e99ff66984269d5675e4abb0e6b46f/packages/sdk/base/src/signatureUtils.ts#L25
        String authHeader = Numeric.toHexString(signatureData.getV()) + Numeric.toHexString(signatureData.getR()).substring(2) + Numeric.toHexString(signatureData.getS()).substring(2);

        // We can sign it ourselves. Ethereum doesn't know celo addresses.
        // String authHeader = contractKit.web3j.ethSign(address, Hash.sha3String(bodyString)).send().getSignature();

        String base64BlindSig;

        try {
            base64BlindSig = SelectiveCall.selectiveRetryAsyncWithBackOff(() -> {
                HttpURLConnection connection = null;
                int responseCode;

                try {
                    connection = (HttpURLConnection) new URL(odisUrl + SIGN_MESSAGE_ENDPOINT).openConnection();

                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", authHeader);
                    connection.getOutputStream().write(bodyString.getBytes());

                    responseCode = connection.getResponseCode();

                    if (responseCode >= 200 && responseCode < 300) {
                        JSONObject response = new JSONObject(InternalUtils.streamToString(connection.getInputStream()));

                        connection.disconnect();

                        return new SignMessageResponse(response);
                    }
                } catch (Exception e) {
                    if (connection != null) {
                        try {
                            connection.disconnect();
                        } catch (Throwable t) { }
                    }

                    throw new CeloException(CeloError.NETWORK_ERROR, e);
                }

                switch (responseCode) {
                    case 403:
                        throw new Exception(ERROR_ODIS_QUOTA);
                    case 400:
                        throw new Exception(ERROR_ODIS_INPUT);
                    case 401:
                        throw new Exception(ERROR_ODIS_AUTH);
                    default:
                        if (responseCode >= 400 && responseCode < 500) {
                            throw new Exception(ERROR_ODIS_CLIENT + " " + responseCode);
                        }

                        throw new Exception("Unknown failure " + responseCode);
                }
            }, 3, ERRORS).combinedSignature;
        } catch (Exception e) {
            throw new CeloException(CeloError.ODIS_ERROR, e);
        }

        try {
            String base64UnblindedSig = blsBlindingClient.unblindMessage(base64BlindSig, odisPubKey);
            byte[] sigBuf = Base64.decode(base64UnblindedSig, Base64.DEFAULT);

            String salt = Base64.encodeToString(Hash.sha256(sigBuf), Base64.DEFAULT).substring(0, PEPPER_CHAR_LENGTH);

            sharedPreferences.edit().putString(target, salt).apply();

            return salt;
        } catch (Exception e) {
            throw new CeloException(CeloError.UNBLINDING_ERROR, e);
        }
    }

    private static class SignMessageResponse {

        private boolean success;
        private String combinedSignature;

        SignMessageResponse(JSONObject json) throws JSONException {
            success = json.getBoolean("success");
            combinedSignature = json.getString("combinedSignature");
        }

    }

}
