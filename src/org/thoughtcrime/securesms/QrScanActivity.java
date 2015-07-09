package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;
import java.util.Collections;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QrScanActivity extends ActionBarActivity implements ZXingScannerView.ResultHandler {
    public static final int REQUEST_SCAN_BARCODE = 1;
    public  static final String FINGERPRINT = "fingerprint";
    private ZXingScannerView mScannerView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mScannerView = new ZXingScannerView(this);

        mScannerView.setFormats(Collections.singletonList(BarcodeFormat.QR_CODE));
        setContentView(mScannerView);
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.setAutoFocus(true);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
        mScannerView.setResultHandler(null);
    }

    @Override
    public void handleResult(Result rawResult) {
        String key = rawResult.getText();
        Intent i = new Intent();
        i.putExtra(FINGERPRINT, key);
        setResult(Activity.RESULT_OK, i);
        finishActivity(REQUEST_SCAN_BARCODE);
        finish();
    }
}
