package works.heymate.celo;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import org.celo.contractkit.ContractKit;
import org.celo.contractkit.Utils;
import org.celo.contractkit.wrapper.AttestationsWrapper;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class CeloSDK {

    private static final String TAG = "CeloSDK";

    private static final int MESSAGE_GET_CONTRACT_KIT = 0;
    private static final int MESSAGE_GET_ADDRESS = 1;
    private static final int MESSAGE_LOOKUP_PHONE_NUMBER = 2;
    private static final int MESSAGE_LOOKUP_PHONE_NUMBER_OWNERSHIP = 3;
    private static final int MESSAGE_GET_BALANCE = 4;

    private static Looper newLooper() {
        HandlerThread thread = new HandlerThread(TAG + "-" + Math.round(Math.random() * 100));
        thread.start();
        return thread.getLooper();
    }

    private final Context mContext;
    private final LocalHandler mLocalHandler;

    private final CeloContext mCeloContext;

    private final Credentials mAccount;

    private final List<ContractKitCallback> mContractKitCallbacks = new ArrayList<>();
    private final List<AddressCallback> mAddressCallbacks = new ArrayList<>(1);
    private final Map<String, List<PhoneNumberLookupCallback>> mPhoneNumberLookupCallbacks = new Hashtable<>(2);
    private final List<PhoneNumberOwnershipLookupCallback> mPhoneNumberOwnershipLookupCallbacks = new ArrayList<>(1);
    private final List<BalanceCallback> mBalanceCallbacks = new ArrayList<>(1);

    private ContractKit mContractKit;

    public CeloSDK(Context context, CeloContext celoContext, CeloAccount account) {
        this(context, celoContext, account, newLooper());
    }

    public CeloSDK(Context context, CeloContext celoContext, CeloAccount account, Looper looper) {
        mContext = context.getApplicationContext();
        mLocalHandler = new LocalHandler(looper);

        mCeloContext = celoContext;

        mAccount = Credentials.create(account.privateKey, account.publicKey);
    }

    public Looper getLooper() {
        return mLocalHandler.getLooper();
    }

    /**
     * callback is called on the looper thread.
     * @param callback
     */
    public void getContractKit(ContractKitCallback callback) {
        synchronized (mContractKitCallbacks) {
            mContractKitCallbacks.add(callback);

            if (mContractKitCallbacks.size() > 1) {
                return;
            }
        }

        mLocalHandler.sendEmptyMessage(MESSAGE_GET_CONTRACT_KIT);
    }

    public void getAddress(AddressCallback callback) {
        mAddressCallbacks.add(callback);

        if (mAddressCallbacks.size() > 1) {
            return;
        }

        mLocalHandler.sendEmptyMessage(MESSAGE_GET_ADDRESS);
    }

    public void lookupPhoneNumber(String phoneNumber, PhoneNumberLookupCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }

        List<PhoneNumberLookupCallback> callbacks = mPhoneNumberLookupCallbacks.get(phoneNumber);

        if (callbacks == null) {
            callbacks = new ArrayList<>(1);

            mPhoneNumberLookupCallbacks.put(phoneNumber, callbacks);
        }

        callbacks.add(callback);

        if (callbacks.size() > 1) {
            return;
        }

        Message message = Message.obtain(mLocalHandler, MESSAGE_LOOKUP_PHONE_NUMBER, phoneNumber);
        mLocalHandler.sendMessage(message);
    }

    public void lookupPhoneNumberOwnership(String phoneNumber, PhoneNumberOwnershipLookupCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }

        mPhoneNumberOwnershipLookupCallbacks.add(callback);

        if (mPhoneNumberOwnershipLookupCallbacks.size() > 1) {
            return;
        }

        mLocalHandler.sendMessage(Message.obtain(mLocalHandler, MESSAGE_LOOKUP_PHONE_NUMBER_OWNERSHIP, phoneNumber));
    }

    public void requestAttestationsForPhoneNumber(String phoneNumber, AttestationRequestCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }

        getContractKit((success, contractKit, errorCause) -> {
            if (errorCause != null) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationRequestResult(false, 0, 0, 0, new CeloException(CeloError.CONTRACT_KIT_ERROR, errorCause)));
                return;
            }

            String salt;

            try {
                salt = ODISSaltUtil.getSalt(mContext, contractKit, mCeloContext.odisURL, mCeloContext.odisPublicKey, phoneNumber);
            } catch (CeloException e) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationRequestResult(false, 0, 0, 0, new CeloException(CeloError.SALTING_ERROR, e)));
                return;
            }

            AttestationRequester.AttestationResult result = AttestationRequester.requestAttestations(contractKit, phoneNumber, salt);

            InternalUtils.runOnMainThread(() -> callback.onAttestationRequestResult(result.countsAreReliable, result.newAttestations, result.totalAttestations, result.completedAttestations, result.errorCause));
        });
    }

    public void completeAttestationForPhoneNumber(String phoneNumber, String code, AttestationCompletionCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }

        getContractKit((success, contractKit, errorCause) -> {
            if (errorCause != null) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationCompletionResult(false, 0, 0, 0, errorCause));
                return;
            }

            String salt;

            try {
                salt = ODISSaltUtil.getSalt(mContext, contractKit, mCeloContext.odisURL, mCeloContext.odisPublicKey, phoneNumber);
            } catch (CeloException e) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationCompletionResult(false, 0, 0, 0, new CeloException(CeloError.SALTING_ERROR, e)));
                return;
            }

            try {
                AttestationCompleter.completeAttestation(mContext, contractKit, phoneNumber, salt, code);
            } catch (CeloException e) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationCompletionResult(false, 0, 0, 0, e));
                return;
            }

            try {
                AttestationsWrapper.AttestationsStatus status = lookupPhoneNumberVerificationStatus(phoneNumber);

                InternalUtils.runOnMainThread(() -> callback.onAttestationCompletionResult(true, status.completed, status.total, status.numAttestationsRemaining, null));
            } catch (CeloException e) {
                InternalUtils.runOnMainThread(() -> callback.onAttestationCompletionResult(true, 0, 0, 0, e));
            }
        });
    }

    public void getBalance(BalanceCallback callback) {
        mBalanceCallbacks.add(callback);

        if (mBalanceCallbacks.size() > 1) {
            return;
        }

        mLocalHandler.sendEmptyMessage(MESSAGE_GET_BALANCE);
    }

    private void getBalanceInternal() {
        try {
            BalanceInfo balanceInfo = getBalanceInfo();

            BigInteger one = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger();

            long cUSD = balanceInfo.cUSD.divide(one.divide(BigInteger.valueOf(100L))).longValue();
            double gold = balanceInfo.gold.divide(one.divide(BigInteger.valueOf(10_000L))).longValue() / 10_000d;

            List<BalanceCallback> callbacks = new ArrayList<>(mBalanceCallbacks);
            mBalanceCallbacks.clear();

            InternalUtils.runOnMainThread(() -> {
                for (BalanceCallback callback: callbacks) {
                    callback.onBalanceResult(true, balanceInfo.cUSD, balanceInfo.gold, cUSD, gold, null);
                }
            });
        } catch (CeloException e) {
            List<BalanceCallback> callbacks = new ArrayList<>(mBalanceCallbacks);
            mBalanceCallbacks.clear();

            InternalUtils.runOnMainThread(() -> {
                for (BalanceCallback callback: callbacks) {
                    callback.onBalanceResult(false, null, null, 0, 0, e);
                }
            });
        }
    }

    private BalanceInfo getBalanceInfo() throws CeloException {
        try {
            ensureContractKit();
        } catch (CeloException e) {
            throw new CeloException(CeloError.CONTRACT_KIT_ERROR, e);
        }

        try {
            BigInteger cUSD = mContractKit.contracts.getStableToken().balanceOf(mContractKit.getAddress()).send();
            BigInteger gold = mContractKit.contracts.getGoldToken().balanceOf(mContractKit.getAddress());

            return new BalanceInfo(cUSD, gold);
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }
    }

    private void lookupPhoneNumberOwnershipInternal(String phoneNumber) {
        try {
            AttestationsWrapper.AttestationsStatus status = lookupPhoneNumberVerificationStatus(phoneNumber);

            List<PhoneNumberOwnershipLookupCallback> callbacks = new ArrayList<>(mPhoneNumberOwnershipLookupCallbacks);
            mPhoneNumberOwnershipLookupCallbacks.clear();

            InternalUtils.runOnMainThread(() -> {
                for (PhoneNumberOwnershipLookupCallback callback: callbacks) {
                    callback.onPhoneNumberOwnershipLookupResult(true, status.isVerified, status.completed, status.total, status.numAttestationsRemaining, null);
                }
            });
        } catch (CeloException e) {
            List<PhoneNumberOwnershipLookupCallback> callbacks = new ArrayList<>(mPhoneNumberOwnershipLookupCallbacks);
            mPhoneNumberOwnershipLookupCallbacks.clear();

            InternalUtils.runOnMainThread(() -> {
                for (PhoneNumberOwnershipLookupCallback callback: callbacks) {
                    callback.onPhoneNumberOwnershipLookupResult(false, false, 0, 0, 0, e);
                }
            });
        }
    }

    private AttestationsWrapper.AttestationsStatus lookupPhoneNumberVerificationStatus(String phoneNumber) throws CeloException {
        try {
            ensureContractKit();
        } catch (CeloException e) {
            throw new CeloException(CeloError.CONTRACT_KIT_ERROR, e);
        }

        String salt;

        try {
            salt = ODISSaltUtil.getSalt(mContext, mContractKit, mCeloContext.odisURL, mCeloContext.odisPublicKey, phoneNumber);
        } catch (CeloException e) {
            throw new CeloException(CeloError.SALTING_ERROR, e);
        }

        byte[] identifier = Utils.getPhoneHash(phoneNumber, salt);

        try {
            return mContractKit.contracts.getAttestations().getVerifiedStatus(
                    identifier, mContractKit.getAddress(),
                    AttestationRequester.NUM_ATTESTATIONS_REQUIRED,
                    AttestationRequester.DEFAULT_ATTESTATION_THRESHOLD
            );
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }
    }

    private void lookupPhoneNumberInternal(String phoneNumber) {
        try {
            List<String> addresses = lookupAddressesForPhoneNumber(phoneNumber);

            InternalUtils.runOnMainThread(() -> {
                List<PhoneNumberLookupCallback> callbacks = mPhoneNumberLookupCallbacks.get(phoneNumber);

                if (callbacks != null) {
                    ArrayList<PhoneNumberLookupCallback> pendingCallbacks = new ArrayList<>(callbacks);
                    callbacks.clear();

                    for (PhoneNumberLookupCallback callback: pendingCallbacks) {
                        callback.onPhoneNumberLookupResult(true, addresses, null);
                    }
                }
            });
        } catch (CeloException e) {
            InternalUtils.runOnMainThread(() -> {
                List<PhoneNumberLookupCallback> callbacks = mPhoneNumberLookupCallbacks.get(phoneNumber);

                if (callbacks != null) {
                    ArrayList<PhoneNumberLookupCallback> pendingCallbacks = new ArrayList<>(callbacks);
                    callbacks.clear();

                    for (PhoneNumberLookupCallback callback: pendingCallbacks) {
                        callback.onPhoneNumberLookupResult(false, null, e);
                    }
                }
            });
        }
    }

    private List<String> lookupAddressesForPhoneNumber(String phoneNumber) throws CeloException {
        try {
            ensureContractKit();
        } catch (CeloException e) {
            throw new CeloException(CeloError.CONTRACT_KIT_ERROR, e);
        }

        String salt;

        try {
            salt = ODISSaltUtil.getSalt(mContext, mContractKit, mCeloContext.odisURL, mCeloContext.odisPublicKey, phoneNumber);
        } catch (CeloException e) {
            throw new CeloException(CeloError.SALTING_ERROR, e);
        }

        byte[] identifier = Utils.getPhoneHash(phoneNumber, salt);

        try {
            return mContractKit.contracts.getAttestations().lookupAccountsForIdentifier(identifier).send();
        } catch (Exception e) {
            throw new CeloException(CeloError.NETWORK_ERROR, e);
        }
    }

    private void getAddressInternal() {
        try {
            ensureContractKit();
        } catch (CeloException e) {
            List<AddressCallback> callbacks = new ArrayList<>(mAddressCallbacks);
            mAddressCallbacks.clear();

            for (AddressCallback callback: callbacks) {
                callback.onAddressResult(false, null, e);
            }
        }

        String address = mContractKit.getAddress();

        List<AddressCallback> callbacks = new ArrayList<>(mAddressCallbacks);
        mAddressCallbacks.clear();

        for (AddressCallback callback: callbacks) {
            callback.onAddressResult(true, address, null);
        }
    }

    private void getContractKitInternal() {
        try {
            ensureContractKit();
        } catch (CeloException e) {
            synchronized (mContractKitCallbacks) {
                for (ContractKitCallback callback: mContractKitCallbacks) {
                    callback.onContractKitResult(false, null, e);
                }
                mContractKitCallbacks.clear();
            }
            return;
        }

        synchronized (mContractKitCallbacks) {
            for (ContractKitCallback callback: mContractKitCallbacks) {
                callback.onContractKitResult(true, mContractKit, null);
            }
            mContractKitCallbacks.clear();
        }
    }

    private void ensureContractKit() throws CeloException {
        if (mContractKit == null) {
            ContractKit contractKit;

            try {
                contractKit = ContractKit.build(new HttpService(mCeloContext.networkAddress));

                contractKit.addAccount(mAccount);

                if (!contractKit.contracts.getAccounts().isAccount(contractKit.getAddress()).send()) {
                    contractKit.contracts.getAccounts().createAccount().send();
                }
            } catch (Throwable t) {
                throw new CeloException(CeloError.NETWORK_ERROR, t);
            }

            mContractKit = contractKit;
        }
    }

    private class LocalHandler extends Handler {

        LocalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CONTRACT_KIT:
                    getContractKitInternal();
                    return;
                case MESSAGE_GET_ADDRESS:
                    getAddressInternal();
                    return;
                case MESSAGE_LOOKUP_PHONE_NUMBER:
                    lookupPhoneNumberInternal((String) msg.obj);
                    return;
                case MESSAGE_LOOKUP_PHONE_NUMBER_OWNERSHIP:
                    lookupPhoneNumberOwnershipInternal((String) msg.obj);
                    return;
                case MESSAGE_GET_BALANCE:
                    getBalanceInternal();
                    return;
            }
        }

    }

    private static class BalanceInfo {

        final BigInteger cUSD;
        final BigInteger gold;

        BalanceInfo(BigInteger cUSD, BigInteger gold) {
            this.cUSD = cUSD;
            this.gold = gold;
        }

    }

}
