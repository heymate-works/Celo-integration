package works.heymate.celoexploration;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.celo.contractkit.ContractKit;
import org.celo.contractkit.Utils;
import org.celo.contractkit.wrapper.AttestationsWrapper;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Mnemonic <-> key
 * New account
 * Attest
 * Balance
 * Phone number to address
 * Transfer money to address
 *
 * Account management
 * -- random
 * -- from mnemonic
 * -- attest phone number
 *
 * Balances + update button
 *
 * Query phone number
 *
 * Transfer to address
 */
public class MainActivity extends AppCompatActivity implements CeloThingyObserver {

    private TextView mTextAddress;
    private TextView mTextPhoneNumber;
    private View mButtonAccountManagement;
    private TextView mTextBalanceGold;
    private TextView mTextBalanceCUSD;
    private View mButtonRefreshBalance;
    private EditText mEditPhoneNumber;
    private View mButtonQuery;
    private EditText mEditQueryResult;
    private EditText mEditAddress;
    private EditText mEditAmount;
    private View mButtonTransfer;

    private CeloThingy mCelo;

    private boolean mAccountAvailable = false;
    private boolean mQuerying = false;
    private boolean mTransferring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextAddress = findViewById(R.id.address);
        mTextPhoneNumber = findViewById(R.id.phonenumber);
        mButtonAccountManagement = findViewById(R.id.accountmanagement);
        mTextBalanceGold = findViewById(R.id.balance_gold);
        mTextBalanceCUSD = findViewById(R.id.balance_cusd);
        mButtonRefreshBalance = findViewById(R.id.refreshbalance);
        mEditPhoneNumber = findViewById(R.id.edit_phonenumber);
        mButtonQuery = findViewById(R.id.query);
        mEditQueryResult = findViewById(R.id.queryresult);
        mEditAddress = findViewById(R.id.targetaddress);
        mEditAmount = findViewById(R.id.amount);
        mButtonTransfer = findViewById(R.id.transfer);

        final TextWatcher textChangeWatcher = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                updateQueryButton();
                updateTransferButton();
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

        };

        mEditPhoneNumber.addTextChangedListener(textChangeWatcher);
        mEditAddress.addTextChangedListener(textChangeWatcher);
        mEditAmount.addTextChangedListener(textChangeWatcher);

        mCelo = CeloThingy.get(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mCelo.refreshAccountInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCelo.observe(this);
        mCelo.refreshBalance();

        updateViewState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mCelo.cancelObservation(this);
    }

    @Override
    public void ohThereIsAChange() {
        updateViewState();
    }

    private void updateViewState() {
        String address = mCelo.getAddress();

        boolean loadingPhoneNumber = mCelo.isLoadingPhoneNumber();
        String phoneNumber = mCelo.getPhoneNumber();
        int phoneNumberVerifiedCount = mCelo.getPhoneNumberVerifiedCount();
        int phoneNumberVerificationRequestCount = mCelo.getPhoneNumberVerificationRequestCount();

        boolean gettingBalance = mCelo.isGettingBalance();
        String goldBalance = mCelo.getGoldBalance();
        String cUSDBalance = mCelo.getCUSDBalance();

        mAccountAvailable = !mCelo.isRefreshing() && address != null;

        if (mAccountAvailable) {
            mTextAddress.setText(address);

            if (gettingBalance) {
                mTextBalanceGold.setText("...");
                mTextBalanceCUSD.setText("...");
                mButtonRefreshBalance.setEnabled(false);
            }
            else {
                mTextBalanceGold.setText(goldBalance);
                mTextBalanceCUSD.setText(cUSDBalance);
                mButtonRefreshBalance.setEnabled(true);
            }
        }
        else {
            mTextAddress.setText("Account is not set.");
            mButtonRefreshBalance.setEnabled(false);
            mTextBalanceGold.setText("_");
            mTextBalanceCUSD.setText("_");
        }

        updateQueryButton();
        updateTransferButton();

        if (loadingPhoneNumber) {
            mTextPhoneNumber.setText("Loading...");
        }
        else if (phoneNumber == null) {
            mTextPhoneNumber.setText("Not assigned.");
        }
        else if (phoneNumberVerificationRequestCount > 0 && phoneNumberVerifiedCount < phoneNumberVerificationRequestCount) {
            mTextPhoneNumber.setText(phoneNumber + " pending verification " + phoneNumberVerifiedCount + "/" + phoneNumberVerificationRequestCount);
        }
        else {
            mTextPhoneNumber.setText(phoneNumber);
        }
    }

    private void updateQueryButton() {
        if (!mAccountAvailable) {
            mButtonQuery.setEnabled(false);
        }
        else {
            mButtonQuery.setEnabled(!mQuerying && mEditPhoneNumber.length() > 0);
        }
    }

    private void updateTransferButton() {
        if (!mAccountAvailable) {
            mButtonTransfer.setEnabled(false);
        }
        else {
            mButtonTransfer.setEnabled(!mTransferring && mEditAddress.length() > 0 && mEditAmount.length() > 0);
        }
    }

    public void accountManagement(View v) {
        startActivity(AccountManagementActivity.getIntent(this));
    }

    public void refreshBalance(View v) {
        mCelo.refreshBalance();
    }

    public void query(View v) {
        mQuerying = true;

        updateQueryButton();

        String error = mCelo.queryPhoneNumber(mEditPhoneNumber.getText().toString(), (PhoneNumberQueryCallback) (success, address) -> {
            if (isFinishing()) {
                return;
            }

            mQuerying = false;

            updateQueryButton();

            if (!success) {
                Toast.makeText(MainActivity.this, "Query failed.", Toast.LENGTH_LONG).show();
                mEditQueryResult.setText("");
            }
            else if (address != null) {
                mEditQueryResult.setText(address);
            }
            else {
                Toast.makeText(MainActivity.this, "Address not found.", Toast.LENGTH_LONG).show();
                mEditQueryResult.setText("");
            }
        });

        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        }
    }

    public void transfer(View v) {
        mTransferring = true;

        updateTransferButton();

        String error = mCelo.transferCUSD(mEditAddress.getText().toString(), mEditAmount.getText().toString(), (TransferCallback) (success, completed) -> {
            if (isFinishing()) {
                return;
            }

            mCelo.refreshBalance();

            mTransferring = false;

            updateTransferButton();

            if (success) {
                if (completed) {
                    Toast.makeText(MainActivity.this, "Transfer completed.", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(MainActivity.this, "Transfer failed for some reason. Probably insufficient balance.", Toast.LENGTH_LONG).show();
                }
            }
            else {
                Toast.makeText(MainActivity.this, "Transfer call failed. Unknown state! SCARRRY!!!", Toast.LENGTH_LONG).show();
            }
        });

        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        }
    }

    public void doTheThing(View v) {
        new Thread() {

            @Override
            public void run() {
                try {
                    ContractKit contractKit = ContractKit.build(new HttpService("https://alfajores-forno.celo-testnet.org"));

//                    byte[] privateKeyBytes = new byte[16];
//                    new SecureRandom().nextBytes(privateKeyBytes);
//
//                    ECKeyPair keyPair = ECKeyPair.create(privateKeyBytes);
//
//                    String privateKey = keyPair.getPrivateKey().toString(16);
//                    String publicKey = keyPair.getPublicKey().toString(16);
//
//                    Credentials credentials = Credentials.create(keyPair);
//                    Credentials otherCredentials = Credentials.create(privateKey, publicKey);

                    String privateKey = "3d5cc74354dba746ab0a298ea4d9da9b";
                    String publicKey = "9d8d7cf5a3786f1394214d738690d06c6c3aaf4ad62baface203b5495c58a8fbf6fa208f11b9ae364753dd0b5c9a59763a92cb2d3bfa3d1c86177bd927336fbd";
                    String address = "0x5b0f6dc1a0fb2b6a490843bb96bb0d4ab1150a9e";
                    Credentials credentials = Credentials.create(privateKey, publicKey);

                    contractKit.addAccount(credentials);

//                    RemoteFunctionCall<TransactionReceipt> createAccountCall = contractKit.contracts.getAccounts().createAccount();
//                    TransactionReceipt receipt = createAccountCall.send();
//                    Log.d("AAA", "create account call sent: " + receipt);

//                    List validators = contractKit.contracts.getValidators().getRegisteredValidators().send();


                    BigInteger uBalance = contractKit.contracts.getStableToken().balanceOf(credentials.getAddress()).send();
                    TransactionReceipt approvalReceipt = contractKit.contracts.getStableToken().approve(contractKit.contracts.getAttestations().getContractAddress(), uBalance).send();
                    Log.d("AAA", "approval receipt: " + approvalReceipt);


//                    byte[] phoneHash = Utils.getPhoneHash("+989124152410", "IAmSalty");
                    byte[] phoneHash = Utils.getPhoneHash("+4915166848938", "IAmSalty");

                    TransactionReceipt attestationRequestReceipt = contractKit.contracts.getAttestations().getContract().request(phoneHash, BigInteger.ONE, contractKit.contracts.getStableToken().getContractAddress()).send();
                    Log.d("AAA", "attestationRequestReceipt: " + attestationRequestReceipt);

                    TransactionReceipt receipt = contractKit.contracts.getAttestations().selectIssuers(phoneHash).send();
                    Log.d("AAA", "selectIssuers: " + receipt);
                    AttestationsWrapper.AttestationStat stat = contractKit.contracts.getAttestations().getAttestationStat(phoneHash, credentials.getAddress());
                    Log.d("AAA", "attestatios status: " + stat);

                    BigInteger balance = contractKit.contracts.getGoldToken().balanceOf(credentials.getAddress());
                    BigInteger one = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger();
                    balance = balance.divide(one);
                    Log.d("AAA", "gold balance is: " + balance);

                    BigInteger cUSD = contractKit.contracts.getStableToken().balanceOf(credentials.getAddress()).send();
                    cUSD = cUSD.divide(one.divide(BigInteger.valueOf(100)));
                    Log.d("AAA", "cUSD balance is: " + cUSD);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

        }.start();

    }

}