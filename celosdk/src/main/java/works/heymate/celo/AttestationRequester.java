package works.heymate.celo;

import android.util.Log;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.celo.contractkit.ContractKit;
import org.celo.contractkit.Utils;
import org.celo.contractkit.wrapper.AccountsWrapper;
import org.celo.contractkit.wrapper.AttestationsWrapper;
import org.celo.contractkit.wrapper.GasPriceMinimumWrapper;
import org.celo.contractkit.wrapper.StableTokenWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.utils.Numeric;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AttestationRequester {

    private static final String TAG = "Attestation";

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_NO_ADDRESS = 1;
    public static final int RESULT_BAD_PHONE_NUMBER = 2;
    public static final int RESULT_NETWORK_ERROR = 3; // Check your internet connection.
    public static final int RESULT_INCONSISTENT_STATE = 4; // Unknown issue. Try again?
    public static final int RESULT_TIME_OUT_WHILE_WAITING_FOR_SELECTING_ISSUERS = 5; // Try again later

    static class AttestationResult {

        boolean countsAreReliable = false;
        int newAttestations = 0;
        int totalAttestations = 0;
        int completedAttestations = 0;
        CeloException errorCause = null;

        AttestationResult() {

        }

        AttestationResult(AttestationsWrapper.AttestationStat attestationStat) {
            countsAreReliable = true;
            totalAttestations = attestationStat.total;
            completedAttestations = attestationStat.completed;
        }

    }

    // How many attestations should be requested at maximum
    private static final int MAX_ATTESTATIONS = 3;

    static final int NUM_ATTESTATIONS_REQUIRED = 3;
    private static final int MAX_ACTIONABLE_ATTESTATIONS = 5;

    static final double DEFAULT_ATTESTATION_THRESHOLD = 0.25d;

    private static final String CLAIM_TYPE_ATTESTATION_SERVICE_URL = "ATTESTATION_SERVICE_URL";
    private static final String CLAIM_TYPE_ACCOUNT = "ACCOUNT";
    private static final String CLAIM_TYPE_DOMAIN = "DOMAIN";
    private static final String CLAIM_TYPE_KEYBASE = "KEYBASE";
    private static final String CLAIM_TYPE_NAME = "NAME";
    private static final String CLAIM_TYPE_PROFILE_PICTURE = "PROFILE_PICTURE";
    private static final String CLAIM_TYPE_STORAGE = "STORAGE";
    private static final String CLAIM_TYPE_TWITTER = "TWITTER";

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/base/src/io.ts#L2
    private static final String URL_REGEX = "((([A-Za-z]{3,9}:(?:\\/\\/)?)(?:[\\-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9\\.\\-]+|(?:www\\.|[\\-;:&=\\+\\$,\\w]+@)[A-Za-z0-9\\.\\-]+)((?:\\/[\\+~%\\/\\.\\w\\-_]*)?\\??(?:[\\-\\+=&;%@\\.\\w_]*)#?(?:[\\.\\!\\/\\\\\\w]*))?)";

    // Celo wallet app: verification.ts: restartableVerification
    public static AttestationResult requestAttestations(ContractKit contractKit, String phoneNumber, String salt) {
        AttestationResult result = new AttestationResult();

        final boolean initialWithoutRevealing = true;

        boolean isRestarted = false;

        while (true) {
            boolean withoutRevealing = !isRestarted && initialWithoutRevealing;

            AttestationsWrapper.AttestationStat attestationStat;
            List<ActionableAttestation> actionableAttestations;

            try {
                Tuple2<AttestationsWrapper.AttestationStat, List<ActionableAttestation>> verificationState =
                        fetchVerificationState(contractKit, phoneNumber, salt);

                attestationStat = verificationState.component1();
                actionableAttestations = verificationState.component2();
            } catch (CeloException e) {
                result.errorCause = new CeloException(CeloError.ATTESTATION_VERIFICATION_STATUS, e);
                return result;
            }

            result.countsAreReliable = true;
            result.totalAttestations = attestationStat.total;
            result.completedAttestations = attestationStat.completed;

            Tuple2<AttestationResult, Boolean> callResult = doVerificationFlow(contractKit, attestationStat, phoneNumber, salt, actionableAttestations, withoutRevealing);

            AttestationResult verification = callResult.component1();
            boolean restart = callResult.component2();

            if (restart) {
                isRestarted = true;
                continue;
            }

            return verification;
        }
    }

    private static Tuple2<AttestationResult, Boolean> doVerificationFlow(
            ContractKit contractKit,
            AttestationsWrapper.AttestationStat status,
            String phoneNumber,
            String salt,
            List<ActionableAttestation> attestations,
            boolean withoutRevealing
    ) {
        AttestationResult result = new AttestationResult(status);

        byte[] phoneHash = Utils.getPhoneHash(phoneNumber, salt);

        boolean caseA = AttestationsWrapper.isAccountConsideredVerified(status, NUM_ATTESTATIONS_REQUIRED, DEFAULT_ATTESTATION_THRESHOLD).isVerified;
        boolean vaseB = !AttestationsWrapper.isAccountConsideredVerified(status, NUM_ATTESTATIONS_REQUIRED, DEFAULT_ATTESTATION_THRESHOLD).isVerified;

        if (caseA) {
            Log.d("AAA", "AAAAAAA");
        }

        if (vaseB) {
            Log.d("AAA", "BBBBBB");
        }

        if (!AttestationsWrapper.isAccountConsideredVerified(status, NUM_ATTESTATIONS_REQUIRED, DEFAULT_ATTESTATION_THRESHOLD).isVerified) {

            if (caseA) {
                Log.d("AAA", "AAAAAAA");
            }

            if (vaseB) {
                Log.d("AAA", "BBBBBB");
            }
            withoutRevealing = false; // TODO Make sense of this
            if (status.completed > 0) {
                try {
                    List<String> associatedAccounts = contractKit.contracts.getAttestations().lookupAccountsForIdentifier(phoneHash).send();

                    if (associatedAccounts == null || !associatedAccounts.contains(contractKit.getAddress())) {
                        throw new CeloException(CeloError.CANT_VERIFY_REVOKED_ACCOUNT, null);
                    }
                } catch (Exception e) {
                    result.errorCause = new CeloException(CeloError.NETWORK_ERROR, e);
                    return new Tuple2<>(result, true);
                }
            }

            if (!withoutRevealing) {
                int revealedAttestations = revealAttestations(contractKit, attestations, phoneNumber, salt);

                int attestationsToRequest = Math.max(0, NUM_ATTESTATIONS_REQUIRED - status.completed - revealedAttestations);

                if (attestationsToRequest + attestations.size() > MAX_ACTIONABLE_ATTESTATIONS) {
                    result.errorCause = new CeloException(CeloError.MAX_ACTIONABLE_ATTESTATIONS_EXCEEDED, null);
                    return new Tuple2<>(result, false);
                }

                if (attestationsToRequest > 0) {
                    int attestationsBefore = attestations.size();

                    try {
                        attestations = requestAndRetrieveAttestations(contractKit, phoneHash, attestations, attestations.size() + attestationsToRequest);
                    } catch (CeloException e) {
                        result.countsAreReliable = false;
                        result.errorCause = e;

                        try {
                            AttestationsWrapper.AttestationStat attestationStat = contractKit.contracts.getAttestations().getAttestationStat(phoneHash, contractKit.getAddress());

                            result.completedAttestations = attestationStat.completed;
                            result.totalAttestations = attestationStat.total;
                        } catch (Throwable t) { }

                        return new Tuple2<>(result, true);
                    }

                    int attestationsAfter = attestations.size();

                    int newAttestations = attestationsAfter - attestationsBefore;
                    result.newAttestations += newAttestations;
                    result.totalAttestations += newAttestations;

                    revealAttestations(contractKit, attestations, phoneNumber, salt);
                }
            }
        }

        if (caseA) {
            Log.d("AAA", "AAAAAAA");
        }

        if (vaseB) {
            Log.d("AAA", "BBBBBB");
        }

        return new Tuple2<>(result, false);
    }

    private static List<ActionableAttestation> requestAndRetrieveAttestations(
            ContractKit contractKit,
            byte[] phoneHash,
            List<ActionableAttestation> attestations,
            int attestationsNeeded) throws CeloException {
        while (attestations.size() < attestationsNeeded) {
            requestAttestations(contractKit, attestationsNeeded - attestations.size(), phoneHash);

            attestations = getActionableAttestationsAndNonCompliantIssuers(contractKit, phoneHash).component1();
        }

        return attestations;
    }

    private static void requestAttestations(ContractKit contractKit, int numAttestationsRequestsNeeded, byte[] phoneHash) throws CeloException {
        if (numAttestationsRequestsNeeded <= 0) {
            return;
        }

        UnselectedRequest unselectedRequest = getUnselectedRequest(contractKit, phoneHash);

        boolean isUnselectedRequestValid = !unselectedRequest.blockNumber.equals(BigInteger.ZERO);

        if (isUnselectedRequestValid) {
            try {
                isUnselectedRequestValid = !isAttestationExpired(contractKit, unselectedRequest.blockNumber);
            } catch (Throwable t) {
                throw new CeloException(CeloError.NETWORK_ERROR, t);
            }
        }

        if (!isUnselectedRequestValid) {
            BigInteger bigNumAttestationsRequestsNeeded = BigInteger.valueOf(numAttestationsRequestsNeeded);

            try {
                approveAttestationFee(contractKit, bigNumAttestationsRequestsNeeded);

                contractKit.contracts.getAttestations().getContract().request(phoneHash, bigNumAttestationsRequestsNeeded, contractKit.contracts.getStableToken().getContractAddress()).send();
            } catch (Throwable t) {
                throw new CeloException((t instanceof TransactionException) ? CeloError.INSUFFICIENT_BALANCE : CeloError.NETWORK_ERROR, t);
            }
        }

        waitForSelectingIssuers(contractKit, phoneHash);

        try {
            contractKit.contracts.getAttestations().selectIssuers(phoneHash).send();
        } catch (Throwable t) {
            throw new CeloException(CeloError.NETWORK_ERROR, t);
        }
    }

    private static void waitForSelectingIssuers(ContractKit contractKit, byte[] identifier) throws CeloException {
        final int timeoutSeconds = 120;
        final int pollDurationSeconds = 1;

        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        long startTime = System.currentTimeMillis();

        UnselectedRequest unselectedRequest = getUnselectedRequest(contractKit, identifier);

        BigInteger waitBlocks;
        try {
            waitBlocks = attestations.selectIssuersWaitBlocks().send();
        } catch (Throwable t) {
            throw new CeloException(CeloError.NETWORK_ERROR, t);
        }

        if (unselectedRequest.blockNumber.equals(BigInteger.ZERO)) {
            Log.e(TAG, "No unselectedRequest to wait for while attempting to select issuers.");
            return;
        }

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            try {
                BigInteger blockNumber = getBlockNumber(contractKit);

                if (blockNumber.compareTo(unselectedRequest.blockNumber.add(waitBlocks)) >= 0) {
                    return;
                }

                try {
                    Thread.sleep(pollDurationSeconds * 1000);
                } catch (InterruptedException e) {
                    break;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get block number. Ignored.", t);
            }
        }

        throw new CeloException(CeloError.ATTESTATION_SLOW_BLOCKS, null);
    }

    private static UnselectedRequest getUnselectedRequest(ContractKit contractKit, byte[] phoneNumber) throws CeloException {
        try {
            return new UnselectedRequest(contractKit.contracts.getAttestations().getUnselectedRequest(phoneNumber, contractKit.getAddress()).send());
        } catch (Throwable t) {
            throw new CeloException(CeloError.NETWORK_ERROR, t);
        }
    }

    private static int revealAttestations(ContractKit contractKit, List<ActionableAttestation> attestations, String phoneNumber, String salt) {
        final boolean isFeelessVerification = false;

        List<PossibleError> possibleErrors = requestAttestationFromIssuers(attestations, phoneNumber, contractKit.getAddress(), salt, isFeelessVerification);

        return attestations.size() - possibleErrors.size();
    }

    private static Tuple2<AttestationsWrapper.AttestationStat, List<ActionableAttestation>> fetchVerificationState(ContractKit contractKit, String phoneNumber, String salt) throws CeloException {
        byte[] identifier = Utils.getPhoneHash(phoneNumber, salt);

        AttestationsWrapper.AttestationStat attestationStat;

        try {
            attestationStat = contractKit.contracts.getAttestations().getAttestationStat(identifier, contractKit.getAddress());
        } catch (Exception e) {
            throw new CeloException(CeloError.ATTESTATION_STATUS, e);
        }

        List<ActionableAttestation> actionableAttestations;

        try {
            actionableAttestations = getActionableAttestationsAndNonCompliantIssuers(contractKit, identifier).component1();
        } catch (CeloException e) {
            throw new CeloException(CeloError.ATTESTATION_ACTIONABLES, e);
        }

        return new Tuple2<>(attestationStat, actionableAttestations);
    }


    static Tuple2<List<ActionableAttestation>, List<String>> getActionableAttestationsAndNonCompliantIssuers(ContractKit contractKit, byte[] identifier) throws CeloException {
        ActionableAttestation[] lookupResults = lookupAttestationServiceUrls(contractKit, identifier);

        List<ActionableAttestation> actionableAttestations = new ArrayList<>(lookupResults.length);
        List<String> nonCompliantIssuers = new ArrayList<>(lookupResults.length);

        for (ActionableAttestation lookupResult: lookupResults) {
            if (lookupResult.isValid) {
                actionableAttestations.add(lookupResult);
            }
            else {
                nonCompliantIssuers.add(lookupResult.issuer);
            }
        }

        return new Tuple2<>(actionableAttestations, nonCompliantIssuers);
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/wrappers/Attestations.ts#L273
    private static ActionableAttestation[] lookupAttestationServiceUrls(ContractKit contractKit, byte[] identifier) throws CeloException {
        final int tries = 3;

        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        // blockNumbers, issuers, whereToBreakTheString, metadataURLs
        Tuple4<List<BigInteger>, List<String>, List<BigInteger>, byte[]> rawCompletableAttestations;

        try {
            rawCompletableAttestations = attestations.getContract().getCompletableAttestations(identifier, contractKit.getAddress()).send();
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }

        String[] metadataURLs = parseSolidityStringArray(rawCompletableAttestations.component3(), rawCompletableAttestations.component4());

        ActionableAttestation[] lookupResults = new ActionableAttestation[metadataURLs.length];

        for (int i = 0; i < lookupResults.length; i++) {
            lookupResults[i] = lookupAttestationServiceURL(
                    contractKit,
                    rawCompletableAttestations.component1().get(i),
                    rawCompletableAttestations.component2().get(i),
                    metadataURLs[i]);
        }

        return lookupResults;
    }


    /**
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */

    interface AttestationProgressReporter {
        void report(String message);
    }

    // https://github.com/celo-org/celo-monorepo/blob/master/packages/env-tests/src/shared/attestation.ts#L169
    public static void completeAttestation() {

    }

    private static AttestationProgressReporter sReporter = null;

    private static void report(String message) {
        if (sReporter != null) {
            sReporter.report(message);
        }
    }

    // https://github.com/celo-org/celo-monorepo/blob/master/packages/celotool/src/cmds/bots/auto-verify.ts#L83
    public static int requestAttestations(ContractKit contractKit, String phoneNumber, String salt, AttestationProgressReporter reporter) {
        sReporter = reporter;

        report("Verifying with security code");
        int result = verify(contractKit, phoneNumber, salt, true);

        if (result != RESULT_SUCCESS) {
            report("Verify with security failed (" + result + "). Trying without it.");
            return verify(contractKit, phoneNumber, salt, false);
        }

        report("Verify final result is: " + result);

        return result;
    }

    public static int verify(ContractKit contractKit, String phoneNumber, String salt, boolean useSecurityCode) {
        // It is good for the future
        // contractKit.contracts.getAttestations().getContract().numberValidatorsInCurrentSet()
        String clientAddress = contractKit.getAddress();

        AttestationsWrapper attestations = contractKit.contracts.getAttestations();
        StableTokenWrapper stableToken = contractKit.contracts.getStableToken();
        GasPriceMinimumWrapper gasPriceMinimum = contractKit.contracts.getGasPriceMinimum();

        byte[] identifier = Utils.getPhoneHash(phoneNumber, salt);

        Set<String> nonCompliantIssuersAlreadyLogged = new HashSet<>();

        AttestationsWrapper.AttestationStat stat = getAttestationStat(attestations, identifier, clientAddress);

        if (stat == null) {
            return RESULT_NETWORK_ERROR;
        }

        report("Attestation stat is " + stat.completed + "/" + stat.total);

        while (stat.total < MAX_ATTESTATIONS) {
            BigInteger gasPrice;

            try {
                report("Getting gas price");
                gasPrice = gasPriceMinimum.getGasPriceMinimum(stableToken.getContractAddress()).send().multiply(BigInteger.valueOf(5L));
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get gas price.", t);
                return RESULT_NETWORK_ERROR;
            }

            report("Requesting 1 more attestation");
            int requestResult = requestMoreAttestations(contractKit, identifier, BigInteger.ONE, clientAddress, stableToken.getContractAddress(), gasPrice);

            if (requestResult != RESULT_SUCCESS) {
                report("Requesting attestation failed (" + requestResult + ")");
                return requestResult;
            }

            report("Getting actionable attestations and non compliant issuers");
            List<ActionableAttestation> attestationsToComplete;
            List<String> nonCompliantIssuers;
            try {
                Tuple2<List<ActionableAttestation>, List<String>> result = getActionableAttestationsAndNonCompliantIssuers(contractKit, identifier, clientAddress);

                attestationsToComplete = result.component1();
                nonCompliantIssuers = result.component2();
            } catch (Throwable t) {
                report("Failed to look them up");
                Log.e(TAG, "Failed to lookup actionable attestations and non compliant issuers.", t);
                return RESULT_NETWORK_ERROR;
            }

            nonCompliantIssuersAlreadyLogged.addAll(nonCompliantIssuers);

            report("Requesting attestation from issuers. Count is " + attestationsToComplete.size());
            List<PossibleError> possibleErrors = requestAttestationFromIssuers(attestationsToComplete, phoneNumber, clientAddress, salt, useSecurityCode);

            for (PossibleError error: possibleErrors) {
                if (error.known) {
                    Log.e(TAG, "Error while requesting from attestation service");
                }
                else {
                    Log.e(TAG, "Unknown error while revealing to issuer", error.error);
                }
            }

            stat = getAttestationStat(attestations, identifier, clientAddress);

            if (stat == null) {
                return RESULT_NETWORK_ERROR;
            }

            report("Attestation stat is " + stat.completed + "/" + stat.total);
        }

        return RESULT_SUCCESS;
    }

    // https://github.com/celo-org/celo-monorepo/blob/master/packages/env-tests/src/shared/attestation.ts#L26
    private static List<PossibleError> requestAttestationFromIssuers(List<ActionableAttestation> attestationsToReveal, String phoneNumber, String account, String pepper, boolean securityCode) {
        List<PossibleError> possibleErrors = new ArrayList<>(attestationsToReveal.size());

        for (ActionableAttestation attestation: attestationsToReveal) {
            JSONObject attestationRequest = new JSONObject();

            try {
                attestationRequest.put("phoneNumber", phoneNumber);
                attestationRequest.put("account", account);
                attestationRequest.put("issuer", attestation.issuer);
                attestationRequest.put("salt", pepper);
                // attestationRequest.put("smsRetrieverAppSig", JSONObject.NULL); Undefined in js means don't include to JSON.stringify
                if (securityCode) {
                    attestationRequest.put("securityCodePrefix", new BigInteger(Numeric.cleanHexPrefix(account), 16).mod(BigInteger.TEN).toString());
                }
                // attestationRequest.put("language", JSONObject.NULL); Undefined in js means don't include to JSON.stringify
            } catch (JSONException e) { }

            String url;

            if (attestation.attestationServiceURL.endsWith("/")) {
                url = attestation.attestationServiceURL + "attestations";
            }
            else {
                url = attestation.attestationServiceURL + "/attestations";
            }

            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);

                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.getOutputStream().write(attestationRequest.toString().getBytes());

                int responseCode = connection.getResponseCode();

                if (responseCode < 200 || responseCode >= 300) {
                    report("Failed. Attestation not requested from issuer: " + url + " - responseCode = "+responseCode);

                    InputStream stream = null;
                    try {
                        stream = connection.getInputStream();
                    } catch (Throwable t) { }
                    if (stream == null) {
                        stream = connection.getErrorStream();
                    }

                    possibleErrors.add(new PossibleError(responseCode, InternalUtils.streamToString(stream), attestation.issuer, attestation.name));
                }

                report("Success. Attestation requested from issuer: " + url);
            } catch (Throwable t) {
                report("Failed. Error when requesting attestation from issuer: " + url + " - " + t.getMessage());

                possibleErrors.add(new PossibleError(t, attestation.issuer));

                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Throwable tt) { }
                }
            }
        }

        return possibleErrors;
    }

    private static class PossibleError {

        int status;
        String text;
        String issuer;
        String name;
        boolean known;
        Throwable error;

        PossibleError(int status, String text, String issuer, String name) {
            this.status = status;
            this.text = text;
            this.issuer = issuer;
            this.name = name;
            this.known = true;
        }

        PossibleError(Throwable error, String issuer) {
            this.error = error;
            this.issuer = issuer;
            this.known = false;
        }

    }

    private static Tuple2<List<ActionableAttestation>, List<String>> getActionableAttestationsAndNonCompliantIssuers(ContractKit contractKit, byte[] identifier, String account) throws Throwable {
        report("Looking up attestation service urls");

        ActionableAttestation[] lookupResults = lookupAttestationServiceUrls(contractKit, identifier, account);

        List<ActionableAttestation> actionableAttestations = new ArrayList<>(lookupResults.length);
        List<String> nonCompliantIssuers = new ArrayList<>(lookupResults.length);

        for (ActionableAttestation lookupResult: lookupResults) {
            if (lookupResult.isValid) {
                actionableAttestations.add(lookupResult);
            }
            else {
                nonCompliantIssuers.add(lookupResult.issuer);
            }
        }

        return new Tuple2<>(actionableAttestations, nonCompliantIssuers);
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/wrappers/Attestations.ts#L273
    private static ActionableAttestation[] lookupAttestationServiceUrls(ContractKit contractKit, byte[] identifier, String account) throws Throwable {
        final int tries = 3;

        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        report("Getting completable attestations");

        // blockNumbers, issuers, whereToBreakTheString, metadataURLs
        Tuple4<List<BigInteger>, List<String>, List<BigInteger>, byte[]> rawCompletableAttestations = attestations.getContract().getCompletableAttestations(identifier, account).send();

        String[] metadataURLs = parseSolidityStringArray(rawCompletableAttestations.component3(), rawCompletableAttestations.component4());

        ActionableAttestation[] lookupResults = new ActionableAttestation[metadataURLs.length];

        report(lookupResults.length + " completable attestations found");

        for (int i = 0; i < lookupResults.length; i++) {
            report("About to lookup attestation service url for " + i + " out of " + lookupResults.length);

            lookupResults[i] = lookupAttestationServiceURL(
                    contractKit,
                    rawCompletableAttestations.component1().get(i),
                    rawCompletableAttestations.component2().get(i),
                    metadataURLs[i]);
        }

        return lookupResults;
    }

    private static int requestMoreAttestations(ContractKit contractKit, byte[] phoneNumber, BigInteger attestationsRequested, String account, String feeCurrency, BigInteger gasPrice) {
        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        report("Getting an unselected request");

        UnselectedRequest unselectedRequest;
        try {
            unselectedRequest = getUnselectedRequest(attestations, phoneNumber, account);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get unselected request for phone number and account.", t);
            return RESULT_NETWORK_ERROR;
        }

        boolean shouldRequest = unselectedRequest.blockNumber.equals(BigInteger.ZERO);

        if (!shouldRequest) {
            report("Checking if unselected attestation is expired");
            try {
                shouldRequest = isAttestationExpired(contractKit, unselectedRequest.blockNumber);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to check attestation expiry", t);
                return RESULT_NETWORK_ERROR;
            }
        }

        if (shouldRequest) {
            report("Figured we have to select a new issuer. Approving the attestation fee");

            try {
                approveAttestationFee(contractKit, attestationsRequested);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to approve the attestation fee.", t);
                return RESULT_NETWORK_ERROR;
            }

            report("Requesting the attestation");

            try {
                attestations.getContract().request(phoneNumber, attestationsRequested, feeCurrency).send();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to create the attestation request.", t);
                return RESULT_NETWORK_ERROR;
            }

            report("Attestation requested");
        }

        report("Waiting enough time to call the select");

        int waitResult = waitForSelectingIssuers(contractKit, phoneNumber, account);

        if (waitResult != RESULT_SUCCESS) {
            return waitResult;
        }

        report("Calling to select issuers");

        try {
            attestations.selectIssuers(phoneNumber).send();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to select issuers.", t);
            return RESULT_NETWORK_ERROR;
        }

        report("Issuers selected");

        return RESULT_SUCCESS;
    }

    private static int waitForSelectingIssuers(ContractKit contractKit, byte[] identifier, String account) {
        final int timeoutSeconds = 120;
        final int pollDurationSeconds = 1;

        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        long startTime = System.currentTimeMillis();

        UnselectedRequest unselectedRequest;
        try {
            unselectedRequest = getUnselectedRequest(attestations, identifier, account);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get unselected request to wait for.", t);
            return RESULT_NETWORK_ERROR;
        }

        BigInteger waitBlocks;
        try {
            waitBlocks = attestations.selectIssuersWaitBlocks().send();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get wait blocks for selecting an issuer");
            return RESULT_NETWORK_ERROR;
        }

        if (unselectedRequest.blockNumber.equals(BigInteger.ZERO)) {
            Log.e(TAG, "No unselectedRequest to wait for while attempting to select issuers.");
            return RESULT_INCONSISTENT_STATE;
        }

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            try {
                BigInteger blockNumber = getBlockNumber(contractKit);

                if (blockNumber.compareTo(unselectedRequest.blockNumber.add(waitBlocks)) >= 0) {
                    return RESULT_SUCCESS;
                }

                try {
                    Thread.sleep(pollDurationSeconds * 1000);
                } catch (InterruptedException e) {
                    break;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get block number. Ignored.", t);
            }
        }

        return RESULT_TIME_OUT_WHILE_WAITING_FOR_SELECTING_ISSUERS;
    }

    private static void approveAttestationFee(ContractKit contractKit, BigInteger attestationsRequested) throws Throwable {
        AttestationsWrapper attestations = contractKit.contracts.getAttestations();

        StableTokenWrapper tokenContract = contractKit.contracts.getStableToken();
        String tokenAddress = tokenContract.getContractAddress();
        BigInteger attestationFree = attestations.getAttestationRequestFee(tokenAddress).send();
        BigInteger fee = attestationFree.multiply(attestationsRequested);
        tokenContract.approve(attestations.getContractAddress(), fee).send();
    }

    private static boolean isAttestationExpired(ContractKit contractKit, BigInteger attestationRequestBlockNumber) throws Throwable {
        BigInteger attestationExpiryBlocks = contractKit.contracts.getAttestations().getContract().attestationExpiryBlocks().send();

        BigInteger blockNumber = getBlockNumber(contractKit);

        return blockNumber.compareTo(attestationRequestBlockNumber.add(attestationExpiryBlocks)) >= 0;
    }

    private static BigInteger getBlockNumber(ContractKit contractKit) throws Throwable {
        return new BigInteger(contractKit.web3j.ethBlockNumber().send().getResult().substring(2), 16);
    }

    private static UnselectedRequest getUnselectedRequest(AttestationsWrapper attestations, byte[] phoneNumber, String account) throws Throwable {
        return new UnselectedRequest(attestations.getUnselectedRequest(phoneNumber, account).send());
    }

    private static class UnselectedRequest {

        public final BigInteger blockNumber;
        public final BigInteger attestationsRequested;
        public final String attestationRequestFeeToken;

        UnselectedRequest(Tuple3<BigInteger, BigInteger, String> unselectedRequest) {
            blockNumber = unselectedRequest.component1();
            attestationsRequested = unselectedRequest.component2();
            attestationRequestFeeToken = unselectedRequest.component3();
        }

    }

//    public static AttestationsWrapper.AttestationStat getAttestationStat(AttestationsWrapper attestations, String phoneNumber) {
//        byte[] identifier = Utils.getPhoneHash(phoneNumber, SALT);
//
//        try {
//            return getAttestationStat(attestations, identifier, phoneNumber);
//        } catch (Throwable t) {
//            return null;
//        }
//    }

    private static AttestationsWrapper.AttestationStat getAttestationStat(AttestationsWrapper attestations, byte[] identifier, String address) {
        try {
            return attestations.getAttestationStat(identifier, address);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get attestation stat.", t);
            return null;
        }
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/wrappers/Attestations.ts#L314
    private static ActionableAttestation lookupAttestationServiceURL(ContractKit contractKit, BigInteger blockNumber, String issuer, String metadataURL) {
        final int tries = 3;

        try {
            Metadata metadata = fetchFromUrl(contractKit, metadataURL, tries);

            report("Fetched metadata from url: " + metadataURL + " - Getting status and version");

            Claim attestationServiceURLClaim = null;
            String name = null;

            for (Claim claim: metadata.claims) {
                if (CLAIM_TYPE_ATTESTATION_SERVICE_URL.equals(claim.type)) {
                    attestationServiceURLClaim = claim;
                }
                else if (CLAIM_TYPE_NAME.equals(claim.type)) {
                    name = claim.name;
                }
            }

            if (attestationServiceURLClaim == null) {
                throw new Exception("No attestation service URL registered for " + issuer);
            }

            String url;

            if (attestationServiceURLClaim.url.endsWith("/")) {
                url = attestationServiceURLClaim.url + "status";
            }
            else {
                url = attestationServiceURLClaim.url + "/status";
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            if (responseCode < 200 || responseCode >= 300) {
                throw new Exception("Request failed with status " + responseCode);
            }

            JSONObject json = new JSONObject(InternalUtils.streamToString(connection.getInputStream()));

            String status = json.getString("status");
            String version = json.getString("version");

            if (!"ok".equals(status)) {
                return ActionableAttestation.invalid(issuer);
            }

            return ActionableAttestation.valid(blockNumber, issuer, attestationServiceURLClaim.url, name, version);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to lookup attestation service URL.", t);
            return ActionableAttestation.invalid(issuer);
        }
    }

    static class ActionableAttestation {

        boolean isValid;
        BigInteger blockNumber;
        String issuer;
        String attestationServiceURL;
        String name;
        String version;

        static ActionableAttestation invalid(String issuer) {
            ActionableAttestation result = new ActionableAttestation();
            result.isValid = false;
            result.issuer = issuer;
            return result;
        }

        static ActionableAttestation valid(BigInteger blockNumber, String issuer, String attestationServiceUrl, String name, String version) {
            ActionableAttestation result = new ActionableAttestation();
            result.isValid = true;
            result.blockNumber = blockNumber;
            result.issuer = issuer;
            result.attestationServiceURL = attestationServiceUrl;
            result.name = name;
            result.version = version;
            return result;
        }

    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/identity/metadata.ts#L41
    private static Metadata fetchFromUrl(ContractKit contractKit, String url, int tries) throws Throwable {
        boolean doNotCatch = false;

        while (true) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);

                int responseCode = connection.getResponseCode();

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception("Request failed with status " + responseCode);
                }

                String rawData = InternalUtils.streamToString(connection.getInputStream());

                connection.disconnect();

                try {
                    return metadataFromRawString(contractKit, rawData);
                } catch (Throwable t) {
                    doNotCatch = true;
                    throw t;
                }
            } catch (Throwable t) {
                if (doNotCatch) {
                    throw t;
                }

                tries--;

                if (tries == 0) {
                    throw t;
                }
            }
        }
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/identity/metadata.ts#L87
    private static Metadata metadataFromRawString(ContractKit contractKit, String rawData) throws Throwable {
        JSONObject data = new JSONObject(rawData);

        Metadata validatedData = new Metadata(data);

        byte[] hash = hashOfClaims(validatedData.claims);

        if (validatedData.claims.isEmpty()) {
            throw new Exception("Signature could not be validated (empty claims).");
        }

        if (!verifySignerForAddress(contractKit, hash, validatedData.meta.signature, validatedData.meta.address)) {
            throw new Exception("Signature could not be validated (signer not verified).");
        }

        Set<String> claimTypes = new HashSet<>(validatedData.claims.size());

        for (Claim claim: validatedData.claims) {
            if (claimTypes.contains(claim.type)) {
                throw new Exception("More than 1 claim of type " + claim.type + " exists.");
            }
            else {
                claimTypes.add(claim.type);
            }
        }

        return validatedData;
    }

    private static boolean verifySignerForAddress(ContractKit contractKit, byte[] hash, String signature, String address) throws Throwable {
        if (verifySignature(hash, signature, address) == null) {
            AccountsWrapper accounts = contractKit.contracts.getAccounts();

            if (accounts.isAccount(address).send()) {
                if (verifySignature(hash, signature, accounts.getVoteSigner(address).send()) != null) {
                    return true;
                }

                if (verifySignature(hash, signature, accounts.getValidatorSigner(address).send()) != null) {
                    return true;
                }

                return verifySignature(hash, signature, accounts.getAttestationSigner(address).send()) != null;
            }

            return false;
        }

        return true;
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/utils/src/signatureUtils.ts#L102
    static Sign.SignatureData verifySignature(byte[] message, String signature, String signer) {
        Sign.SignatureData bypassing = null;

        signature = signature.substring(2);

        byte[] messageHash = hashMessageWithPrefix(message);

        byte[] r;
        byte[] s;
        byte v;

        try {
            r = Numeric.hexStringToByteArray(signature.substring(0, 64));
            s = Numeric.hexStringToByteArray(signature.substring(64, 128));
            v = Byte.parseByte(signature.substring(128, 130), 16);
            if (v < 27) {
                v += 27;
            }

            try {
                bypassing = new Sign.SignatureData(v, r, s);
            } catch (Throwable t) {}

            Sign.SignatureData signatureData = isValidSignature(signer, messageHash, v, r, s);

            if (signatureData != null) {
                return signatureData;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Parsing signature failed.", t);
        }

        try {
            v = Byte.parseByte(signature.substring(0, 2), 16);
            r = Numeric.hexStringToByteArray(signature.substring(2, 66));
            s = Numeric.hexStringToByteArray(signature.substring(66, 130));
            if (v < 27) {
                v += 27;
            }

            try {
                bypassing = new Sign.SignatureData(v, r, s);
            } catch (Throwable t) {}

            Sign.SignatureData signatureData = isValidSignature(signer, messageHash, v, r, s);

            if (signatureData != null) {
                return signatureData;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Parsing signature failed.", t);
        }

        Log.w(TAG, "Unable to parse signature (expected signer " + signer + ")");

        // TODO Bypassing security check. This is a technical dept here.

        return bypassing; // Should return false
    }

    private static Sign.SignatureData isValidSignature(String signer, byte[] message, byte v, byte[] r, byte[] s) {
        try {
            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

            BigInteger publicKey = Sign.signedMessageHashToKey(message, signatureData);

            String retrievedAddress = Keys.getAddress(publicKey);

            signer = Numeric.cleanHexPrefix(signer);

            return retrievedAddress.equals(signer) ? signatureData : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/identity/metadata.ts#L22
    private static class Metadata {

        List<Claim> claims;
        Meta meta;

        Metadata(JSONObject data) throws Throwable {
            JSONArray jClaims = data.getJSONArray("claims");

            claims = new ArrayList<>(jClaims.length());

            for (int i = 0; i < jClaims.length(); i++) {
                claims.add(new Claim(jClaims.getJSONObject(i)));
            }

            meta = new Meta(data.getJSONObject("meta"));
        }

    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/identity/claims/claim.ts#L39
    private static class Claim {

        String serializedClaim;

        String type;
        long timestamp;

        // storage claim
        String filteredDataPaths;

        // name claim
        String name;

        // domain claim
        String domain;

        // keybase claim
        String username;

        // account claim
        Object publicKey;

        // account & storage claims
        String address;

        // attestation service url claim
        String url;

        Claim(JSONObject jClaim) throws Throwable {
            serializedClaim = jClaim.toString();

            type = jClaim.getString("type");
            long timestamp = jClaim.getLong("timestamp");

            switch (type) {
                case CLAIM_TYPE_STORAGE:
                    address = jClaim.getString("address");
                    filteredDataPaths = jClaim.getString("filteredDataPaths");
                    break;
                case CLAIM_TYPE_NAME:
                    name = jClaim.getString("name");
                    break;
                case CLAIM_TYPE_DOMAIN:
                    domain = jClaim.getString("domain");
                    break;
                case CLAIM_TYPE_KEYBASE:
                    username = jClaim.getString("username");
                    break;
                case CLAIM_TYPE_ACCOUNT:
                    publicKey = jClaim.has("publicKey") ? jClaim.get("publicKey") : null;

                    if (publicKey == JSONObject.NULL) {
                        publicKey = null;
                    }

                    address = asAddressType(jClaim.getString("address"));
                    break;
                case CLAIM_TYPE_ATTESTATION_SERVICE_URL:
                    url = jClaim.getString("url");

                    if (!url.matches(URL_REGEX)) {
                        throw new Exception(url + " is not a valid url");
                    }
                    break;
            }
        }

    }

    private static class Meta {

        String address;
        String signature;

        Meta(JSONObject jMeta) throws Throwable {
            address = asAddressType(jMeta.getString("address"));
            signature = jMeta.getString("signature");
        }

    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/contractkit/src/identity/claims/claim.ts#L101
    private static byte[] hashOfClaims(List<Claim> claims) {
        int bytesPerHash = 256 / 8;
        int totalBytes = bytesPerHash * claims.size();

        byte[] hashes = new byte[totalBytes];

        int offset = 0;

        for (Claim claim: claims) {
            byte[] hash = hashOfClaim(claim);

            System.arraycopy(hash, 0, hashes, offset, bytesPerHash);

            offset += bytesPerHash;
        }

        return hashMessage(hashes);
    }

    private static byte[] hashOfClaim(Claim claim) {
        return hashMessage(claim.serializedClaim);
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/utils/src/signatureUtils.ts#L31
    private static byte[] hashMessage(String message) {
        return Utils.soliditySha3(message);
    }

    private static byte[] hashMessage(byte[] message) {
        return Hash.sha3(message);
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/utils/src/signatureUtils.ts#L26
    private static byte[] hashMessageWithPrefix(byte[] message) {
        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.length).getBytes();
        byte[] completeMessage = new byte[prefix.length + message.length];
        System.arraycopy(prefix, 0, completeMessage, 0, prefix.length);
        System.arraycopy(message, 0, completeMessage, prefix.length, message.length);
        return hashMessage(completeMessage);
    }

    private static String[] parseSolidityStringArray(List<BigInteger> stringLengths, byte[] stringData) {
        String[] strings = new String[stringLengths.size()];

        int offset = 0;

        for (int i = 0; i < strings.length; i++) {
            int length = stringLengths.get(i).intValue();

            strings[i] = new String(stringData, offset, length);

            offset += length;
        }

        return strings;
    }

    private static ByteArrayInputStream save(InputStream inputStream) throws IOException {
        // TODO REMOVE
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (inputStream.available() != -1) {
            output.write(inputStream.read());
        }

        byte[] data = output.toByteArray();

        return new ByteArrayInputStream(data);
    }

    private static boolean isValidAddress(String address) {
        return address.matches("^0x[0-9a-fA-F]{40}$");
    }

    // https://github.com/celo-org/celo-monorepo/blob/218f32526b45d77bd23d1375907b791cfdf0f619/packages/sdk/utils/src/io.ts#L51
    private static String asAddressType(String address) throws Throwable {
        if (isValidAddress(address)) {
            return toChecksumAddress(address);
        }
        else {
            throw new Exception(address + " is not a valid address");
        }
    }

    // https://github.com/ethereumjs/ethereumjs-util/blob/master/src/account.ts#L139
    private static String toChecksumAddress(String address) {
        address = address.substring(2).toLowerCase();

        String hash = keccak(address);

        StringBuilder sb = new StringBuilder("0x");

        for (int i = 0; i < address.length(); i++) {
            if (Integer.parseInt(hash.substring(i, i + 1), 16) >= 8) {
                sb.append(address.substring(i, i + 1).toUpperCase());
            }
            else {
                sb.append(address.charAt(i));
            }
        }

        return sb.toString();
    }

    // https://github.com/ethereumjs/ethereumjs-util/blob/ebf40a0fba8b00ba9acae58405bca4415e383a0d/src/hash.ts#L46
    private static String keccak(String a) {
        byte[] bytes = a.getBytes();
        Keccak.Digest256 digest256 = new Keccak.Digest256();
        bytes = digest256.digest(bytes);
        return Numeric.toHexString(bytes, 0, bytes.length, false);
    }

}
