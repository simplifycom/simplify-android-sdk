package com.simplify.android.sdk.sample;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.fragment.WalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.google.android.gms.wallet.fragment.WalletFragmentMode;
import com.google.android.gms.wallet.fragment.WalletFragmentOptions;
import com.google.android.gms.wallet.fragment.WalletFragmentStyle;
import com.simplify.android.sdk.CardEditorKotlin;
import com.simplify.android.sdk.SimplifyAndroidPayCallback;
import com.simplify.android.sdk.SimplifyCallback;
import com.simplify.android.sdk.Simplify;
import com.simplify.android.sdk.SimplifyMap;
import com.simplify.android.sdk.SimplifySecure3DCallback;


public class MainActivity extends AppCompatActivity implements SimplifyAndroidPayCallback, SimplifySecure3DCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    static final String TAG = MainActivity.class.getSimpleName();

    static final String WALLET_FRAGMENT_ID = "wallet_fragment";

    GoogleApiClient mGoogleApiClient;
    CardEditorKotlin mCardEditor;
    Button mPayButton;
    Simplify simplify;
    ProgressBar mProgressBar;


    //---------------------------------------------
    // Life-Cycle
    //---------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // connect to google api client
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        // disconnect from google api client
        mGoogleApiClient.disconnect();

        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // let the Simplify SDK marshall out the android pay activity results
        if (Simplify.handleAndroidPayResult(requestCode, resultCode, data, this)) {
            return;
        } else if (Simplify.handle3DSResult(requestCode, resultCode, data, this)) {
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    //---------------------------------------------
    // Android Pay callback methods
    //---------------------------------------------

    @Override
    public void onReceivedMaskedWallet(MaskedWallet maskedWallet) {
        // launch confirmation activity
        Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
        intent.putExtra(WalletConstants.EXTRA_MASKED_WALLET, maskedWallet);
        startActivity(intent);
    }

    @Override
    public void onReceivedFullWallet(FullWallet fullWallet) {

    }

    @Override
    public void onAndroidPayCancelled() {

    }

    @Override
    public void onAndroidPayError(int errorCode) {
        Log.e(TAG, "Android Pay error code: " + errorCode);
    }


    //---------------------------------------------
    // 3DS callback methods
    //---------------------------------------------

    @Override
    public void onSecure3DComplete(boolean success) {
        // TODO - If 3DS authentication was successful, this is where you would send the token ID
        // TODO - and payment information back to your server for processing...

        Toast.makeText(this, "3DS authentication " + (success ? "success" : "failure"), Toast.LENGTH_SHORT).show();

        mProgressBar.setVisibility(View.GONE);
        mPayButton.setEnabled(true);

        Intent i = new Intent(MainActivity.this, ThankYouActivity.class);
        i.putExtra(ThankYouActivity.EXTRA_PAGE, success ? ThankYouActivity.PAGE_SUCCESS : ThankYouActivity.PAGE_FAIL);
        startActivity(i);
    }

    @Override
    public void onSecure3DError(String message) {
        Toast.makeText(this, "3DS authentication encountered an error: " + message, Toast.LENGTH_SHORT).show();

        mProgressBar.setVisibility(View.GONE);
        mPayButton.setEnabled(true);

        Intent i = new Intent(MainActivity.this, ThankYouActivity.class);
        i.putExtra(ThankYouActivity.EXTRA_PAGE, ThankYouActivity.PAGE_FAIL);
        startActivity(i);
    }

    @Override
    public void onSecure3DCancel() {
        Toast.makeText(this, "3DS authentication cancelled", Toast.LENGTH_SHORT).show();

        mProgressBar.setVisibility(View.GONE);
        mPayButton.setEnabled(true);
    }


    //---------------------------------------------
    // Google API Client callback methods
    //---------------------------------------------

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, connectionResult.getErrorMessage());
    }

    //---------------------------------------------
    // Util
    //---------------------------------------------

    void init() {

        simplify = ((SimplifyApplication) getApplication()).getSimplify();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                        .setEnvironment(Constants.WALLET_ENVIRONMENT)
                        .setTheme(WalletConstants.THEME_LIGHT)
                        .build())
                .build();

        TextView amountView = (TextView) findViewById(R.id.amount);
        amountView.setText(Constants.AMOUNT);

        mPayButton = (Button) findViewById(R.id.btnPay);
        mPayButton.setEnabled(false);
        mPayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                requestCardToken();
            }
        });

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        mCardEditor = (CardEditorKotlin) findViewById(R.id.card_editor);
        mCardEditor.addOnStateChangedListener(new CardEditorKotlin.OnStateChangedListener() {
            @Override
            public void onStateChange(CardEditorKotlin cardEditor) {
                mPayButton.setEnabled(cardEditor.isValid());
            }
        });

        IsReadyToPayRequest req = IsReadyToPayRequest.newBuilder()
                .addAllowedCardNetwork(WalletConstants.CardNetwork.MASTERCARD)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.VISA)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.AMEX)
                .addAllowedCardNetwork(WalletConstants.CardNetwork.DISCOVER)
                .build();

        Wallet.Payments.isReadyToPay(mGoogleApiClient, req)
                .setResultCallback(new ResultCallback<BooleanResult>() {
                    @Override
                    public void onResult(@NonNull BooleanResult booleanResult) {
                        if (booleanResult.getStatus().isSuccess()) {
                            if (booleanResult.getValue()) {
                                Log.i(TAG, "Android Pay is ready");
                                showAndroidPayButton();

                                return;
                            }
                        }

                        Log.i(TAG, "Android Pay not ready");
                        hideAndroidPayButton();
                    }
                });
    }

    void showAndroidPayButton() {

        findViewById(R.id.buy_button_layout).setVisibility(View.VISIBLE);

        // Define fragment style
        WalletFragmentStyle fragmentStyle = new WalletFragmentStyle()
                .setBuyButtonText(WalletFragmentStyle.BuyButtonText.BUY_WITH)
                .setBuyButtonAppearance(WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_DARK)
                .setBuyButtonWidth(WalletFragmentStyle.Dimension.MATCH_PARENT);

        // Define fragment options
        WalletFragmentOptions fragmentOptions = WalletFragmentOptions.newBuilder()
                .setEnvironment(Constants.WALLET_ENVIRONMENT)
                .setFragmentStyle(fragmentStyle)
                .setTheme(WalletConstants.THEME_LIGHT)
                .setMode(WalletFragmentMode.BUY_BUTTON)
                .build();

        // Create a new instance of WalletFragment
        WalletFragment walletFragment = WalletFragment.newInstance(fragmentOptions);

        // Initialize the fragment with start params
        // Note: If using the provided helper method Simplify.handleAndroidPayResult(int, int, Intent),
        //       you MUST set the request code to Simplify.REQUEST_CODE_MASKED_WALLET
        WalletFragmentInitParams startParams = WalletFragmentInitParams.newBuilder()
                .setMaskedWalletRequest(getMaskedWalletRequest())
                .setMaskedWalletRequestCode(Simplify.REQUEST_CODE_MASKED_WALLET)
                .build();

        walletFragment.initialize(startParams);

        // Add Wallet fragment to the UI
        getFragmentManager().beginTransaction()
                .replace(R.id.buy_button_holder, walletFragment, WALLET_FRAGMENT_ID)
                .commit();

    }

    void hideAndroidPayButton() {
        findViewById(R.id.buy_button_layout).setVisibility(View.GONE);
    }

    void requestCardToken() {

        mProgressBar.setVisibility(View.VISIBLE);
        mPayButton.setEnabled(false);

        SimplifyMap card = mCardEditor.getCard();

        SimplifyMap secure3DRequestData = new SimplifyMap()
                .set("amount", 100)
                .set("currency", Constants.CURRENCY_CODE)
                .set("description", "Iced coffee");

        simplify.createCardToken(card, secure3DRequestData, new SimplifyCallback() {
            @Override
            public void onSuccess(@NonNull SimplifyMap cardToken) {

                Toast.makeText(MainActivity.this, "Card token created: " + cardToken.get("id"), Toast.LENGTH_SHORT).show();

                // TODO cache cardToken

                // check if 3DS data present
                if (cardToken.containsKey("card.secure3DData.isEnrolled") && (boolean) cardToken.get("card.secure3DData.isEnrolled")) {
                    // start 3DS activity
                    Simplify.start3DSActivity(MainActivity.this, cardToken);
                    return;
                }


                // TODO - If not performing 3DS authentication, this is where you would send the token ID
                // TODO - and payment information back to your server for processing...

                mProgressBar.setVisibility(View.GONE);
                mPayButton.setEnabled(true);

                Intent i = new Intent(MainActivity.this, ThankYouActivity.class);
                i.putExtra(ThankYouActivity.EXTRA_PAGE, ThankYouActivity.PAGE_SUCCESS);
                startActivity(i);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();

                mProgressBar.setVisibility(View.GONE);
                mPayButton.setEnabled(true);

                Intent i = new Intent(MainActivity.this, ThankYouActivity.class);
                i.putExtra(ThankYouActivity.EXTRA_PAGE, ThankYouActivity.PAGE_FAIL);
                startActivity(i);
            }
        });
    }


    MaskedWalletRequest getMaskedWalletRequest() {

        PaymentMethodTokenizationParameters parameters =
                PaymentMethodTokenizationParameters.newBuilder()
                        .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.NETWORK_TOKEN)
                        .addParameter("publicKey", simplify.getAndroidPayPublicKey())
                        .build();

        Cart cart = Cart.newBuilder()
                .setCurrencyCode(Constants.CURRENCY_CODE)
                .setTotalPrice(Constants.AMOUNT)
                .addLineItem(LineItem.newBuilder()
                        .setCurrencyCode(Constants.CURRENCY_CODE)
                        .setDescription("Iced Coffee")
                        .setQuantity("1")
                        .setUnitPrice(Constants.AMOUNT)
                        .setTotalPrice(Constants.AMOUNT)
                        .build())
                .build();

        return MaskedWalletRequest.newBuilder()
                .setMerchantName("Overpriced Coffee Shop")
                .setPhoneNumberRequired(true)
                .setShippingAddressRequired(true)
                .setCurrencyCode(Constants.CURRENCY_CODE)
                .setCart(cart)
                .setEstimatedTotalPrice(Constants.AMOUNT)
                .setPaymentMethodTokenizationParameters(parameters)
                .build();
    }

}
