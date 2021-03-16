package works.heymate.celoexploration;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Current account
 *
 * -- random
 * -- from mnemonic
 *
 * Set phone number
 */
public class AccountManagementActivity extends AppCompatActivity implements CeloThingyObserver {

    public static Intent getIntent(Context context) {
        return new Intent(context, AccountManagementActivity.class);
    }

    private TextView mTextAccount;
    private View mButtonNewAccount;
    private View mButtonFromMnemonic;
    private TextView mPhoneNumber;
    private View mButtonSetPhoneNumber;
    private TextView mTextMnemonic;

    private CeloThingy mCelo;

    private boolean mAccountAvailable = false;
    private boolean mSettingPhoneNumber = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accountmanagement);

        mTextAccount = findViewById(R.id.account);
        mButtonNewAccount = findViewById(R.id.newaccount);
        mButtonFromMnemonic = findViewById(R.id.frommnemonic);
        mPhoneNumber = findViewById(R.id.phonenumber);
        mButtonSetPhoneNumber = findViewById(R.id.setphonenumber);
        mTextMnemonic = findViewById(R.id.mnemonic);

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
        String mnemonic = mCelo.getMnemonic();

        mAccountAvailable = !mCelo.isRefreshing() && address != null;

        boolean loadingPhoneNumber = mCelo.isLoadingPhoneNumber();
        String phoneNumber = mCelo.getPhoneNumber();
        int phoneNumberVerifiedCount = mCelo.getPhoneNumberVerifiedCount();
        int phoneNumberVerificationRequestCount = mCelo.getPhoneNumberVerificationRequestCount();

        mButtonNewAccount.setEnabled(!mCelo.isRefreshing());
        mButtonFromMnemonic.setEnabled(!mCelo.isRefreshing());

        if (mAccountAvailable) {
            mTextAccount.setText(address);
            mTextMnemonic.setText(mnemonic);
        }
        else {
            mTextAccount.setText("Account is not set.");
            mTextMnemonic.setText("");
        }

        if (loadingPhoneNumber) {
            mPhoneNumber.setText("Loading...");
            mPhoneNumber.setEnabled(false);
        }
        else if (phoneNumber == null) {
            mPhoneNumber.setText("Not assigned.");
            mPhoneNumber.setEnabled(true);
        }
        else if (phoneNumberVerificationRequestCount > 0 && phoneNumberVerifiedCount < phoneNumberVerificationRequestCount) {
            mPhoneNumber.setText(phoneNumber + " pending verification " + phoneNumberVerifiedCount + "/" + phoneNumberVerificationRequestCount);
        }
        else {
            mPhoneNumber.setText(phoneNumber);
        }

        updateSetPhoneNumberButton();
    }

    private void updateSetPhoneNumberButton() {
        if (!mAccountAvailable) {
            mButtonSetPhoneNumber.setEnabled(false);
        }
        else {
            mButtonSetPhoneNumber.setEnabled(!mSettingPhoneNumber);
        }
    }

    public void newAccount(View v) {
        mCelo.setRandomAccount();

        updateViewState();
    }

    public void fromMnemonic(View v) {
        final EditText input = new EditText(this);
        input.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("Type in your mnemonic");
        input.setMinLines(2);

        new AlertDialog.Builder(this)
                .setTitle("Account From Mnemonic")
                .setView(input)
                .setPositiveButton("Set", (dialog, which) -> {
                    String text = input.getText().toString().trim();

                    if (text.length() == 0) {
                        Toast.makeText(AccountManagementActivity.this, "Mnemonic is empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String error = mCelo.setAccountFromMnemonic(text);

                    if (error != null) {
                        Toast.makeText(AccountManagementActivity.this, error, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateViewState();
                    dialog.dismiss();
                })
                .show();
    }

    private AlertDialog mProgressDialog = null;

    public void setPhoneNumber(View v) {
        final EditText input = new EditText(this);
        input.setInputType(EditorInfo.TYPE_CLASS_PHONE);
        input.setHint("Phone number");

        // TODO Remove
//        input.setText("+380731940555");
//        input.setText("+4915166848938");
//        input.setText("+989124152410");
        input.setText("+4917670176202");


        new AlertDialog.Builder(this)
                .setTitle("Set a phone number")
                .setView(input)
                .setPositiveButton("Set", (dialog, which) -> {
                    String text = input.getText().toString().trim();

                    if (text.length() == 0) {
                        Toast.makeText(AccountManagementActivity.this, "Enter a phone number.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String error = mCelo.attestPhoneNumber(text, new AttestationRequestCallback() {

                        @Override
                        public void progressUpdate(String update) {
                            if (mProgressDialog != null) {
                                mProgressDialog.setMessage(update);
                            }
                        }

                        @Override
                        public void onAttestationRequestResult(boolean success, boolean requested, String message) {
                            mSettingPhoneNumber = false;
                            if (!isFinishing()) {
                                updateSetPhoneNumberButton();

                                if (mProgressDialog != null) {
                                    mProgressDialog.dismiss();
                                }
                            }

                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }

                    });

                    if (error != null) {
                        Toast.makeText(AccountManagementActivity.this, error, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mSettingPhoneNumber = true;
                    updateSetPhoneNumberButton();

                    mProgressDialog = new AlertDialog.Builder(this)
                            .setTitle("Processing attestation")
                            .setMessage("Initiating...")
                            .setCancelable(false)
                            .show();

                    updateViewState();
                    dialog.dismiss();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

    }
}
