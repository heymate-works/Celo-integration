package works.heymate.celo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import org.celo.contractkit.ContractKit;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.http.HttpService;

import java.util.ArrayList;
import java.util.List;

public class CeloSDK {

    private static final String TAG = "CeloSDK";

    public static final String NETWORK_MAIN = ContractKit.MAINNET;
    public static final String NETWORK_ALFAJORES = ContractKit.ALFAJORES_TESTNET;
    public static final String NETWORK_BAKLAVA = ContractKit.BAKLAVA_TESTNET;

    private static final int MESSAGE_CHECK_PHONE_NUMBER_STATUS = 0;

    private static Looper newLooper() {
        HandlerThread thread = new HandlerThread(TAG + "-" + Math.round(Math.random() * 100));
        thread.start();
        return thread.getLooper();
    }

    private final Context mContext;
    private final LocalHandler mLocalHandler;

    private final String mNetwork;

    private Credentials mAccount;

    private final List<VerifiedPhoneNumbersCallback> mVerifiedPhoneNumbersCallbacks = new ArrayList<>(1);

    private ContractKit mContractKit;

    public CeloSDK(Context context, String network, String privateKey, String publicKey) {
        this(context, network, privateKey, publicKey, newLooper());
    }

    public CeloSDK(Context context, String network, String privateKey, String publicKey, Looper looper) {
        mContext = context.getApplicationContext();
        mLocalHandler = new LocalHandler(looper);

        mNetwork = network;

        mAccount = Credentials.create(privateKey, publicKey);
    }

    public void getVerifiedPhoneNumbers(VerifiedPhoneNumbersCallback callback) {
//        mContractKit.contracts.getAttestations().revoke()
        synchronized (mVerifiedPhoneNumbersCallbacks) {
            mVerifiedPhoneNumbersCallbacks.add(callback);

            if (mVerifiedPhoneNumbersCallbacks.size() > 1) {
                return;
            }
        }

        mLocalHandler.sendEmptyMessage(MESSAGE_CHECK_PHONE_NUMBER_STATUS);
    }

    private void checkPhoneNumberStatusInternal() {
//        ensureInitialization();

        // TODO
    }

    private void ensureInitialization() throws Throwable {
        if (mContractKit == null) {
            mContractKit = ContractKit.build(new HttpService(mNetwork));
        }

        SharedPreferences preferences = mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE);

    }

    private class LocalHandler extends Handler {

        LocalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CHECK_PHONE_NUMBER_STATUS:
                    checkPhoneNumberStatusInternal();
                    return;
            }
        }

    }

}
