package works.heymate.celoexploration;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import org.celo.contractkit.ContractKit;
import org.celo.contractkit.Utils;
import org.celo.contractkit.wrapper.AttestationsWrapper;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.novacrypto.bip39.MnemonicGenerator;
import io.github.novacrypto.bip39.wordlists.English;
import works.heymate.celo.ODISSaltUtil;

public class CeloThingy extends Handler {

    private static final String TAG = CeloThingy.class.getSimpleName();

    private static final String ALFAJORES = "https://alfajores-forno.celo-testnet.org";
    private static final String BAKLAVA = "https://baklava-forno.celo-testnet.org";
    private static final String NETWORK = ALFAJORES;

    private static final String PRIVATE_KEY = "private_key";
    private static final String PUBLIC_KEY = "public_key";
    private static final String PHONE_NUMBER = "phone_number";

    private static final int REFRESH = 0;
    private static final int LOAD_PHONE_NUMBER = 1;
    private static final int REFRESH_BALANCE = 2;
    private static final int QUERY_PHONE_NUMBER = 3;
    private static final int SET_RANDOM_ACCOUNT = 4;
    private static final int SET_ACCOUNT = 5;

    private static CeloThingy mInstance = null;

    public static CeloThingy get(Context context) {
        if (mInstance == null) {
            HandlerThread thread = new HandlerThread("CeloThread");
            thread.start();

            mInstance = new CeloThingy(context.getApplicationContext(), thread.getLooper());
        }

        return mInstance;
    }

    private Context mContext;
    private SharedPreferences mPreferences;

    private Handler mMainThread;

    private ContractKit mContractKit;

    private boolean mRefreshing = false;
    private String mAddress = null;
    private int mRequestedAttestations = 0;
    private int mCompletedAttestations = 0;

    private boolean mLoadingPhoneNumber = false;
    private String mPhoneNumber = null;

    private boolean mRefreshingBalance = false;
    private BigInteger mGoldBalance = null;
    private BigInteger mCUSDBalance = null;

    private final Map<String, List<PhoneNumberQueryCallback>> mPhoneNumberQueries = new Hashtable<>();

    private Set<CeloThingyObserver> mObservers = new HashSet<>(2);

    private CeloThingy(Context context, Looper looper) {
        super(looper);

        mContext = context;
        mPreferences = mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE);;

        mMainThread = new Handler(Looper.getMainLooper());
    }

    public void refreshAccountInfo() {
        sendEmptyMessage(REFRESH);
    }

    public void refreshBalance() {
        sendEmptyMessage(REFRESH_BALANCE);
    }

    public String getAddress() {
        return mAddress;
    }

    public String getMnemonic() {
        final ContractKit contractKit = mContractKit;

        if (contractKit == null) {
            return null;
        }

        Credentials credentials = contractKit.transactionManager.wallet.getDefaultAccount();

        if (credentials == null) {
            return null;
        }

        byte[] privateKey = credentials.getEcKeyPair().getPrivateKey().toByteArray();

        MnemonicGenerator generator = new MnemonicGenerator(English.INSTANCE);

        StringBuilder mnemonicBuilder = new StringBuilder();

        generator.createMnemonic(privateKey, mnemonicBuilder::append);

        return mnemonicBuilder.toString();
    }

    public boolean isRefreshing() {
        return mRefreshing;
    }

    public boolean isLoadingPhoneNumber() {
        return mRefreshing || mLoadingPhoneNumber;
    }

    public String getPhoneNumber() {
        return mPhoneNumber;
    }

    public int getPhoneNumberVerifiedCount() {
        return mCompletedAttestations;
    }

    public int getPhoneNumberVerificationRequestCount() {
        return mRequestedAttestations;
    }

    public boolean isGettingBalance() {
        return mRefreshingBalance;
    }

    public String getGoldBalance() {
        final BigInteger gold = mGoldBalance;

        if (gold == null) {
            return null;
        }

        BigInteger one = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger();
        long longGold = gold.divide(one.divide(BigInteger.valueOf(10000L))).longValue();

        return (longGold / 10000L) + "." + (longGold % 10000L);
    }

    public String getCUSDBalance() {
        final BigInteger cUSD = mCUSDBalance;

        if (cUSD == null) {
            return null;
        }

        BigInteger one = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger();
        long longCUSD = cUSD.divide(one.divide(BigInteger.valueOf(100L))).longValue();

        long cents = longCUSD % 100;

        return "$" + (longCUSD / 100L) + (cents > 0 ? "." + cents : "");
    }

    public String queryPhoneNumber(String phoneNumber, PhoneNumberQueryCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            return "Invalid phone number format.";
        }

        synchronized (mPhoneNumberQueries) {
            List<PhoneNumberQueryCallback> callbacks = mPhoneNumberQueries.get(phoneNumber);

            if (callbacks != null) {
                callbacks.add(callback);
                return null;
            }
            else {
                callbacks = new ArrayList<>(1);
                callbacks.add(callback);

                mPhoneNumberQueries.put(phoneNumber, callbacks);
            }
        }

        Message message = Message.obtain(this);
        message.what = QUERY_PHONE_NUMBER;
        message.obj = phoneNumber;

        sendMessage(message);
        return null;
    }

    public String transferCUSD(String address, String amountStr, TransferCallback callback) {
        try {
            double dAmount = Double.parseDouble(amountStr);

            if (dAmount <= 0) {
                return "Amount must be greater than zero.";
            }

            BigDecimal one = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER);

            BigInteger amount = BigDecimal.valueOf(dAmount).multiply(one).toBigInteger();

            post(() -> {
                if (mContractKit == null || mAddress == null) {
                    runOnMainThread(() -> {
                        callback.onTransferResult(false, false);
                    });
                    return;
                }

                try {
                    TransactionReceipt receipt = mContractKit.contracts.getStableToken().transfer(address, amount).send();

                    runOnMainThread(() -> {
                        callback.onTransferResult(true, receipt.getTransactionHash() != null);
                    });
                } catch (Throwable t) {
                    Log.e(TAG, "Transaction failed", t);
                    runOnMainThread(() -> {
                        callback.onTransferResult(false, false);
                    });
                }
            });
        } catch (NumberFormatException e) {
            return "Amount format is invalid.";
        }

        return null;
    }

    public void setRandomAccount() {
        sendEmptyMessage(SET_RANDOM_ACCOUNT);
    }

    public String setAccountFromMnemonic(String mnemonic) {
        try {
            byte[] privateKey = MnemonicUtils.generateEntropy(mnemonic);

            Message message = Message.obtain(this);
            message.what = SET_ACCOUNT;
            message.obj = privateKey;

            sendMessage(message);
            return null;
        } catch (Throwable t) {
            return "Invalid mnemonic (" + t.getMessage() + ")";
        }
    }


    public String attestPhoneNumber(String phoneNumber, AttestationRequestCallback callback) {
        if (!Utils.E164_REGEX.matcher(phoneNumber).matches()) {
            return "Invalid phone number.";
        }

        class AttestationMessageHolder {

            String message = null;

        }

        final AttestationMessageHolder messageHolder = new AttestationMessageHolder();

        post(() -> {
//            int result = AttestationCarnage.requestAttestations(mContractKit, phoneNumber, message -> {
//                runOnMainThread(() -> {
//                    Log.i(TAG, "Attestation Progress: " + message);
//                    callback.progressUpdate(message);
//                });
//            });
//            runOnMainThread(() -> {
//                callback.onAttestationRequestResult(result != AttestationCarnage.RESULT_NETWORK_ERROR, result == AttestationCarnage.RESULT_SUCCESS, "Hellllooooooo");
//            });
        });

        /*
        post(() -> {
            if (mContractKit == null || mAddress == null) {
                runOnMainThread(() -> {
                    callback.onAttestationRequestResult(false, false, "Account is not set.");
                });
                return;
            }

            // getCompletableAttestations -> blockNumbers, issuers, whereToBreakTheString, metadataURLs

            byte[] phoneHash = Utils.getPhoneHash(phoneNumber, "IAmSalty");

            try {
                postAttestationUpdate("Querying max attestations possible...", callback);
                messageHolder.message = "Failed to get max attestations possible.";
                long maxAttestations = mContractKit.contracts.getAttestations().getContract().maxAttestations().send().longValue();

                long attestationRequestCount = Math.min(3L, maxAttestations);

                if (attestationRequestCount <= 0) {
                    messageHolder.message = "Attestation in Celo is currently impossible.";
                    runOnMainThread(() -> {
                        callback.onAttestationRequestResult(true, false, messageHolder.message);
                    });
                    return;
                }

                postAttestationUpdate("Getting attestation request fee for " + attestationRequestCount + " requests...", callback);
                messageHolder.message = "Failed to get the attestation fee.";
                BigInteger attestationFee = mContractKit.contracts.getAttestations().getAttestationRequestFee(mContractKit.contracts.getStableToken().getContractAddress()).send();

                BigInteger requiredBalance = attestationFee.multiply(BigInteger.valueOf(attestationRequestCount));

                postAttestationUpdate("Checking if cUSD balance is greater than " + requiredBalance + "...", callback);
                messageHolder.message = "Failed to get balance for attestation.";
                BigInteger balance = mContractKit.contracts.getStableToken().balanceOf(mAddress).send();

                if (requiredBalance.compareTo(balance) > 0) {
                    messageHolder.message = "Insufficient balance.";
                    runOnMainThread(() -> {
                        callback.onAttestationRequestResult(true, false, messageHolder.message);
                    });
                    return;
                }

                postAttestationUpdate("Approving the transfer of cUSD to attestation contract...", callback);
                messageHolder.message = "Failed to approve for the attestation fee.";
                mContractKit.contracts.getStableToken().approve(mContractKit.contracts.getAttestations().getContractAddress(), requiredBalance).send();

                postAttestationUpdate("Creating the attestation request...", callback);
                messageHolder.message = "Failed to create the attestation request.";
                mContractKit.contracts.getAttestations().getContract().request(phoneHash, BigInteger.valueOf(attestationRequestCount), mContractKit.contracts.getStableToken().getContractAddress()).send();

                sendEmptyMessage(LOAD_PHONE_NUMBER);
                sendEmptyMessage(REFRESH_BALANCE);

                postAttestationUpdate("Killing 120 seconds for the request to settle because Celo has a bug!", callback);
                // Literally have to wait for previous calls to settle! They have fixed it in future Celo.
                // Read https://github.com/celo-org/celo-monorepo/commit/af0f47b9ce0b69e854d53097b569c0ca78d8f9d9
                Thread.sleep(120_000);

                postAttestationUpdate("Asking the contract to select the issuers to do the thing...", callback);
                messageHolder.message = "Failed to trigger the attestation.";
                mContractKit.contracts.getAttestations().selectIssuers(phoneHash).send();



                runOnMainThread(() -> {
                    callback.onAttestationRequestResult(true, true, "You should receive " + attestationRequestCount + " SMS messages shortly.");
                });
            } catch (Throwable t) {
                Log.e(TAG, "Failed to finish attestation request: " + messageHolder.message, t);

                runOnMainThread(() -> {
                    callback.onAttestationRequestResult(false, false, messageHolder.message);
                });
            }
        });
        */
        return null;
    }

    private void postAttestationUpdate(String message, AttestationRequestCallback callback) {
        runOnMainThread(() -> {
            callback.progressUpdate(message);
        });
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case REFRESH:
                if (mRefreshing) {
                    return;
                }

                mRefreshing = true;
                notifyObservers();

                ensureContractKit();

                if (mAddress == null) {
                    String privateKey = mPreferences.getString(PRIVATE_KEY, null);
                    String publicKey = mPreferences.getString(PUBLIC_KEY, null);

                    if (privateKey != null && publicKey != null) {
                        Credentials credentials = Credentials.create(privateKey, publicKey);
                        mContractKit.addAccount(credentials);
                        mAddress = mContractKit.getAddress();
                    }
                }

                if (mAddress != null) {
                    sendEmptyMessage(LOAD_PHONE_NUMBER);
                }

                mRefreshing = false;
                notifyObservers();
                return;
            case LOAD_PHONE_NUMBER:
                if (mLoadingPhoneNumber || mContractKit == null || mAddress == null || mPhoneNumber != null) {
                    return;
                }

                mLoadingPhoneNumber = true;
                notifyObservers();

                String phoneNumber = mPreferences.getString(PHONE_NUMBER, null);

                if (phoneNumber != null) {
                    try {
                        byte[] phoneHash = Utils.getPhoneHash(phoneNumber, "IAmSalty");

                        List<?> addresses = mContractKit.contracts.getAttestations().lookupAccountsForIdentifier(phoneHash).send();

                        String publicKey = mContractKit.transactionManager.wallet.getDefaultAccount().getEcKeyPair().getPublicKey().toString(16);

                        if (addresses != null && !addresses.isEmpty()) {
                            for (Object address: addresses) {
                                if (mAddress.equals(address) || publicKey.equals(address)) {
                                    if (mAddress.equals(address)) {
                                        Log.i(TAG, "Phone number query matched with address.");
                                    }
                                    else {
                                        Log.i(TAG, "Phone number query matched with public key.");
                                    }

                                    mPhoneNumber = phoneNumber;
                                    mRequestedAttestations = 0;
                                    mCompletedAttestations = 0;

                                    mLoadingPhoneNumber = false;
                                    notifyObservers();
                                    return;
                                }
                            }
                        }

                        AttestationsWrapper.AttestationStat attestationStat = mContractKit.contracts.getAttestations().getAttestationStat(phoneHash, mAddress);

                        mRequestedAttestations = attestationStat.total;
                        mCompletedAttestations = attestationStat.completed;

                        if (mRequestedAttestations > 0) {
                            mPhoneNumber = phoneNumber;
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to refresh the phone number.", t);
                    }
                }

                mLoadingPhoneNumber = false;
                notifyObservers();
                return;
            case REFRESH_BALANCE:
                if (mRefreshingBalance || mContractKit == null || mAddress == null) {
                    return;
                }

                mRefreshingBalance = true;
                notifyObservers();

                try {
                    mGoldBalance = mContractKit.contracts.getGoldToken().balanceOf(mAddress);
                    mCUSDBalance = mContractKit.contracts.getStableToken().balanceOf(mAddress).send();
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to refresh balance.", t);
                }

                mRefreshingBalance = false;
                notifyObservers();
                return;
            case QUERY_PHONE_NUMBER:
                String query = (String) msg.obj;

                boolean querySuccessful = false;
                String queryAddress = null;

                if (mContractKit != null && mAddress != null) {
                    try {
                        byte[] queryHash = Utils.getPhoneHash(query, null);

                        List<?> addresses = mContractKit.contracts.getAttestations().lookupAccountsForIdentifier(queryHash).send();

                        querySuccessful = true;

                        if (addresses != null && !addresses.isEmpty()) {
                            queryAddress = addresses.get(0).toString();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to query phone number.", t);
                    }
                }

                final boolean queryWasSuccessful = querySuccessful;
                final String finalQueryAddress = queryAddress;

                runOnMainThread(() -> {
                    List<PhoneNumberQueryCallback> callbacksToBeNotified = null;

                    synchronized (mPhoneNumberQueries) {
                        List<PhoneNumberQueryCallback> callbacks = mPhoneNumberQueries.remove(query);

                        if (callbacks != null) {
                            callbacksToBeNotified = new ArrayList<>(callbacks);
                        }
                    }

                    if (callbacksToBeNotified != null) {
                        for (PhoneNumberQueryCallback callback: callbacksToBeNotified) {
                            callback.onPhoneNumberQueryResult(queryWasSuccessful, finalQueryAddress);
                        }
                    }
                });
                return;
            case SET_RANDOM_ACCOUNT:
                mRefreshing = true;
                notifyObservers();

                byte[] privateKeyBytes = new byte[16];
                new SecureRandom().nextBytes(privateKeyBytes);

                ECKeyPair keyPair = ECKeyPair.create(privateKeyBytes);

                String privateKey = keyPair.getPrivateKey().toString(16);
                String publicKey = keyPair.getPublicKey().toString(16);

                mPreferences.edit()
                        .putString(PRIVATE_KEY, privateKey)
                        .putString(PUBLIC_KEY, publicKey)
                        .apply();

                mAddress = null;

                mRefreshing = false;

                sendEmptyMessage(REFRESH);
                return;
            case SET_ACCOUNT:
                mRefreshing = true;
                notifyObservers();

                privateKeyBytes = (byte[]) msg.obj;

                keyPair = ECKeyPair.create(privateKeyBytes);

                privateKey = keyPair.getPrivateKey().toString(16);
                publicKey = keyPair.getPublicKey().toString(16);

                mPreferences.edit()
                        .putString(PRIVATE_KEY, privateKey)
                        .putString(PUBLIC_KEY, publicKey)
                        .apply();

                mAddress = null;

                mRefreshing = false;

                sendEmptyMessage(REFRESH);
                return;
        }
    }

    private void ensureContractKit() {
        if (mContractKit == null) {
            try {
                mContractKit = ContractKit.build(new HttpService(NETWORK));
            } catch (Throwable t) {
                Log.e(TAG, "Failed to initiate the contract kit.", t);

                throw new RuntimeException("Failed to initiate the contract kit.", t);
            }
        }
    }

    private void notifyObservers() {
        runOnMainThread(() -> {
            for (CeloThingyObserver observer: mObservers) {
                observer.ohThereIsAChange();
            }
        });
    }

    public void observe(CeloThingyObserver observer) {
        mObservers.add(observer);
    }

    public void cancelObservation(CeloThingyObserver observer) {
        mObservers.remove(observer);
    }

    private void runOnMainThread(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        }
        else {
            mMainThread.post(r);
        }
    }

}