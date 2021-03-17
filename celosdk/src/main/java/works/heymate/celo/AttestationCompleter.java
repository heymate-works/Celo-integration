package works.heymate.celo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.celo.contractkit.ContractKit;
import org.celo.contractkit.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

class AttestationCompleter {

    private static final String TAG = "Attestation";

    private static final String ATTESTATION_CODE_REGEX = "(.* |^)(?:celo:\\/\\/wallet\\/v\\/)?([a-zA-Z0-9=\\+\\/_-]{87,88})($| .*)";
    private static final String ATTESTATION_CODE_PREFIX = "celo://wallet/v/";

    private static final int CODE_LENGTH = 8;
    private static final String NULL_ADDRESS = "0x0000000000000000000000000000000000000000";

    // Valora app : verification.ts : attestationCodeReceiver
    public static void completeAttestation(Context context, ContractKit contractKit, String phoneNumber, String salt, String code) throws CeloException {
        code = code.replaceAll("([¿§])", "_");  // Someone doesn't know why this is happening. I don't even know what this is.

        SharedPreferences sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);

        Set<String> previousCodes = sharedPreferences.getStringSet(phoneNumber, new HashSet<>());

        if (previousCodes.contains(code)) {
            throw new CeloException(CeloError.ATTESTATION_CODE_USED, null);
        }

        byte[] identifier = Utils.getPhoneHash(phoneNumber, salt);

        List<AttestationRequester.ActionableAttestation> attestations = AttestationRequester.getActionableAttestationsAndNonCompliantIssuers(contractKit, identifier).component1();

        String attestationCode;

        if (code.matches(ATTESTATION_CODE_REGEX)) {
            // https://github.com/celo-org/celo-monorepo/blob/79d0efaf50e99ff66984269d5675e4abb0e6b46f/packages/sdk/base/src/attestations.ts#L53
            int prefixIndex = code.indexOf(ATTESTATION_CODE_PREFIX);

            if (prefixIndex < 0) {
                throw new CeloException(CeloError.INVALID_ATTESTATION_CODE, null);
            }

            attestationCode = Numeric.toHexString(Base64.decode(code.substring(prefixIndex + ATTESTATION_CODE_PREFIX.length()), Base64.DEFAULT));
        }
        else {
            if (code.length() != CODE_LENGTH) {
                throw new CeloException(CeloError.INVALID_ATTESTATION_CODE, null);
            }

            attestationCode = getAttestationCodeForSecurityCode(contractKit, phoneNumber, salt, code, attestations);
        }

        String issuer = findMatchingIssuer(contractKit, identifier, attestations, attestationCode);

        if (issuer == null) {
            throw new CeloException(CeloError.INVALID_ATTESTATION_CODE, null);
        }

        Sign.SignatureData signatureData = validateAttestationCode(contractKit, issuer, identifier, attestationCode);

        boolean isValidRequest = signatureData != null;

        if (!isValidRequest) {
            throw new CeloException(CeloError.INVALID_ATTESTATION_CODE, null);
        }

        BigInteger v = new BigInteger(signatureData.getV());

        try {
            contractKit.contracts.getAttestations().complete(identifier, v, signatureData.getR(), signatureData.getS()).send();
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }

        previousCodes.add(code);
        sharedPreferences.edit().putStringSet(phoneNumber, previousCodes).apply();
    }

    private static Sign.SignatureData validateAttestationCode(ContractKit contractKit, String issuer, byte[] identifier, String code) throws CeloException {
        String attestationSigner;

        try {
            attestationSigner = contractKit.contracts.getAccounts().getAttestationSigner(issuer).send();
        } catch (Throwable t) {
            throw new CeloException(CeloError.NETWORK_ERROR, t);
        }

        byte[] expectedSourceMessage = getAttestationMessageToSignFromIdentifier(identifier, contractKit.getAddress());

        Sign.SignatureData signatureData = AttestationRequester.verifySignature(expectedSourceMessage, code, attestationSigner);

        BigInteger v = new BigInteger(signatureData.getV());

        try {
            String result = contractKit.contracts.getAttestations().getContract().validateAttestationCode(identifier, contractKit.getAddress(), v, signatureData.getR(), signatureData.getS()).send();

            return !NULL_ADDRESS.equalsIgnoreCase(result) ? signatureData : null;
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }
    }

    // https://github.com/celo-org/celo-monorepo/blob/master/packages/sdk/contractkit/src/wrappers/Attestations.ts#414
    private static String findMatchingIssuer(ContractKit contractKit, byte[] identifier, List<AttestationRequester.ActionableAttestation> attestations, String code) throws CeloException {
        byte[] expectedSourceMessage = getAttestationMessageToSignFromIdentifier(identifier, contractKit.getAddress());

        for (AttestationRequester.ActionableAttestation attestation: attestations) {
            String attestationSigner;

            try {
                attestationSigner = contractKit.contracts.getAccounts().getAttestationSigner(attestation.issuer).send();
            } catch (Throwable t) {
                throw new CeloException(CeloError.NETWORK_ERROR, t);
            }

            if (AttestationRequester.verifySignature(expectedSourceMessage, code, attestationSigner) != null) {
                return attestation.issuer;
            }
        }

        return null;
    }

    // https://github.com/celo-org/celo-monorepo/blob/52afb2f30b05840faf5ac990255fda825c5e3225/packages/sdk/utils/src/attestations.ts#L33
    private static byte[] getAttestationMessageToSignFromIdentifier(byte[] identifier, String account) {
        byte[] accountBytes = Numeric.hexStringToByteArray(account);

        byte[] message = new byte[identifier.length + accountBytes.length];
        System.arraycopy(identifier, 0, message, 0, identifier.length);
        System.arraycopy(accountBytes, 0, message, identifier.length, accountBytes.length);

        return Hash.sha3(message);
    }

    // Valora app :securityCode.ts : getAttestationForSecurityCode
    private static String getAttestationCodeForSecurityCode(ContractKit contractKit, String phoneNumber, String salt, String securityCode, List<AttestationRequester.ActionableAttestation> attestations) {
        String securityCodePrefix = securityCode.substring(0, 1);

        List<AttestationRequester.ActionableAttestation> lookupAttestations = new ArrayList<>(1);

        for (AttestationRequester.ActionableAttestation attestation: attestations) {
            if (securityCodePrefix.equals(getSecurityCodePrefix(attestation.issuer))) {
                lookupAttestations.add(attestation);
            }
        }

        for (AttestationRequester.ActionableAttestation attestation: lookupAttestations) {
            String result = requestValidator(contractKit, attestation, phoneNumber, salt, securityCode);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private static String requestValidator(ContractKit contractKit, AttestationRequester.ActionableAttestation attestation, String phoneNumber, String salt, String securityCode) {
        // https://github.com/celo-org/celo-monorepo/blob/52afb2f30b05840faf5ac990255fda825c5e3225/packages/sdk/contractkit/src/wrappers/Attestations.ts#L608
        StringBuilder urlParams = new StringBuilder();

        addToURLParams(urlParams, "phoneNumber", phoneNumber);
        addToURLParams(urlParams, "account", contractKit.getAddress());
        addToURLParams(urlParams, "issuer", attestation.issuer);
        addToURLParams(urlParams, "salt", salt);
        addToURLParams(urlParams, "securityCode", securityCode);

        String typedData = buildSecurityCodeTypedData(securityCode);
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(typedData.getBytes(), contractKit.transactionManager.getCredentials().getEcKeyPair());
        String authHeader = Numeric.toHexString(signatureData.getV()) + Numeric.toHexString(signatureData.getR()).substring(2) + Numeric.toHexString(signatureData.getS()).substring(2);

        String url = attestation.attestationServiceURL;

        if (!url.endsWith("/")) {
            url = url + "/";
        }

        url = url + "get_attestations?" + urlParams.toString();

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestProperty("Authentication", authHeader);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                JSONObject body = new JSONObject(InternalUtils.streamToString(connection.getInputStream()));

                connection.disconnect();

                return body.getString("attestationCode");
            }
            else {
                String text = InternalUtils.streamToString(connection.getErrorStream());

                connection.disconnect();

                Log.e(TAG, "Error getting security code for " + attestation.issuer + ". " + responseCode + ": " + text);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to request validator", t);
        }

        return null;
    }

    // https://github.com/celo-org/celo-monorepo/blob/79d0efaf50e99ff66984269d5675e4abb0e6b46f/packages/sdk/utils/src/typed-data-constructors.ts#L3
    private static String buildSecurityCodeTypedData(String code) {
        JSONObject json = new JSONObject();

        try {
            JSONObject types = new JSONObject();

            JSONArray tempArray = new JSONArray();

            JSONObject temp = new JSONObject();
            temp.put("name", "name");
            temp.put("type", "string");
            tempArray.put(temp);

            temp = new JSONObject();
            temp.put("name", "version");
            temp.put("type", "string");
            tempArray.put(temp);

            types.put("EIP712Domain", tempArray);

            tempArray = new JSONArray();
            temp = new JSONObject();
            temp.put("name", "code");
            temp.put("type", "string");
            tempArray.put(temp);

            types.put("AttestationRequest", tempArray);

            json.put("types", types);
            json.put("primaryType", "AttestationRequest");

            temp = new JSONObject();
            temp.put("name", "Attestations");
            temp.put("version", "1.0.0");
            json.put("domain", temp);

            temp = new JSONObject();
            temp.put("code", code);
            json.put("message", temp);
        } catch (JSONException e) { }

        return json.toString();
    }

    private static void addToURLParams(StringBuilder urlParam, String key, String value) {
        try {
            urlParam.append(key).append("=").append(URLEncoder.encode(value, "UTF-8")).append("&");
        } catch (UnsupportedEncodingException e) { }
    }

    private static String getSecurityCodePrefix(String address) {
        return new BigInteger(Numeric.cleanHexPrefix(address), 16).mod(BigInteger.TEN).toString();
    }

}
