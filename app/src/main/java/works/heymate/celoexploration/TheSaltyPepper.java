package works.heymate.celoexploration;

import android.util.Base64;

import org.celo.BlindThresholdBlsModule;
import org.celo.contractkit.ContractKit;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TheSaltyPepper {

    // ODIS Alfajores
    private static final String odisUrl = "https://us-central1-celo-phone-number-privacy.cloudfunctions.net";
    private static final String odisPubKey = "kPoRxWdEdZ/Nd3uQnp3FJFs54zuiS+ksqvOm9x8vY6KHPG8jrfqysvIRU0wtqYsBKA7SoAsICMBv8C/Fb2ZpDOqhSqvr/sZbZoHmQfvbqrzbtDIPvUIrHgRS0ydJCMsA";

    // ODIS Alfajores Staging
    // private static final odisUrl = "https://us-central1-celo-phone-number-privacy-stg.cloudfunctions.net";
    // private static final String odisPubKey = "7FsWGsFnmVvRfMDpzz95Np76wf/1sPaK0Og9yiB+P8QbjiC8FV67NBans9hzZEkBaQMhiapzgMR6CkZIZPvgwQboAxl65JWRZecGe5V3XO4sdKeNemdAZ2TzQuWkuZoA";

    // ODIS MainNet
    // private static final String odisUrl = "https://us-central1-celo-pgpnp-mainnet.cloudfunctions.net";
    //private static final String odisPubKey = "FvreHfLmhBjwxHxsxeyrcOLtSonC9j7K3WrS4QapYsQH6LdaDTaNGmnlQMfFY04Bp/K4wAvqQwO9/bqPVCKf8Ze8OZo8Frmog4JY4xAiwrsqOXxug11+htjEe1pj4uMA";

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
    public static String getSaltForPepper(ContractKit contractKit, String phoneNumber) throws Exception {
        String address = contractKit.getAddress();

        if (address == null) {
            return null;
        }

        BlindThresholdBlsModule blsBlindingClient = new BlindThresholdBlsModule();

        String base64BlindedMessage = blsBlindingClient.blindMessage(Base64.encodeToString(phoneNumber.getBytes(), Base64.DEFAULT));


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

//        String authHeader = contractKit.web3j.ethSign(address, Hash.sha3String(bodyString)).send().getSignature();

        String base64BlindSig = SelectiveCall.selectiveRetryAsyncWithBackOff(() -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(odisUrl + SIGN_MESSAGE_ENDPOINT).openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", authHeader);
            connection.getOutputStream().write(bodyString.getBytes());

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                JSONObject response = new JSONObject(AttestationCarnage.streamToString(connection.getInputStream()));

                connection.disconnect();

                return new SignMessageResponse(response);
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

        String base64UnblindedSig = blsBlindingClient.unblindMessage(base64BlindSig, odisPubKey);
        byte[] sigBuf = Base64.decode(base64UnblindedSig, Base64.DEFAULT);

        return Base64.encodeToString(Hash.sha256(sigBuf), Base64.DEFAULT).substring(0, PEPPER_CHAR_LENGTH);
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
