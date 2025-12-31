package org.signal.donations.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.WalletConstants;

import org.signal.core.util.logging.Log;
import org.signal.core.util.money.FiatMoney;
import org.signal.donations.GooglePayApi;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements GooglePayApi.PaymentRequestCallback {

  private static final String TAG = Log.tag(MainActivity.class);

  private View donateButton;

  private GooglePayApi payApi;
  private Disposable   isReadyToPayDisposable;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    donateButton = findViewById(R.id.donate_with_googlepay);
    donateButton.setVisibility(View.GONE);
    donateButton.setOnClickListener(v -> requestPayment());

    payApi = new GooglePayApi(this, TestUtil.INSTANCE, new GooglePayApi.Configuration(WalletConstants.ENVIRONMENT_TEST));

    isReadyToPayDisposable = payApi.queryIsReadyToPay().subscribe(this::presentGooglePayButton, this::presentException);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    payApi.onActivityResult(requestCode, resultCode, data, 1, this);
  }

  @Override
  protected void onDestroy() {
    isReadyToPayDisposable.dispose();
    super.onDestroy();
  }

  private void presentGooglePayButton() {
    Log.d(TAG, "Pay is available, displaying button");
    donateButton.setVisibility(View.VISIBLE);
  }

  private void presentException(@NonNull Throwable throwable) {
    Log.w(TAG, "Could not display pay button", throwable);
    Toast.makeText(this, "Could not display pay button", Toast.LENGTH_LONG).show();
  }

  private void requestPayment() {
    donateButton.setClickable(false);

    payApi.requestPayment(new FiatMoney(BigDecimal.valueOf(4.00), Currency.getInstance(Locale.getDefault())), "Test Purchase", 1);
  }

  @Override
  public void onSuccess(PaymentData paymentData) {
    Toast.makeText(this, "SUCCESS", Toast.LENGTH_SHORT).show();
    donateButton.setClickable(true);
  }

  @Override
  public void onError(@NonNull GooglePayApi.GooglePayException googlePayException) {
    Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show();
    donateButton.setClickable(true);
  }

  @Override
  public void onCancelled() {
    Toast.makeText(this, "CANCELLED", Toast.LENGTH_SHORT).show();
    donateButton.setClickable(true);
  }
}
