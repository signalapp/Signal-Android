package org.thoughtcrime.securesms;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.signal.qr.QrScannerView;
import org.signal.qr.kitkat.ScanListener;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.ViewUtil;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class DeviceAddFragment extends LoggingFragment {

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  private ImageView     devicesImage;
  private ScanListener  scanListener;
  private QrScannerView scannerView;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    ViewGroup container = ViewUtil.inflate(inflater, viewGroup, R.layout.device_add_fragment);

    this.scannerView = container.findViewById(R.id.scanner);
    this.devicesImage = container.findViewById(R.id.devices);
    ViewCompat.setTransitionName(devicesImage, "devices");

    container.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                 int oldLeft, int oldTop, int oldRight, int oldBottom)
      {
        v.removeOnLayoutChangeListener(this);

        Animator reveal = ViewAnimationUtils.createCircularReveal(v, right, bottom, 0, (int) Math.hypot(right, bottom));
        reveal.setInterpolator(new DecelerateInterpolator(2f));
        reveal.setDuration(800);
        reveal.start();
      }
    });

    scannerView.start(getViewLifecycleOwner(), CameraXModelBlocklist.isBlocklisted());

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    Disposable qrDisposable = scannerView
        .getQrData()
        .distinctUntilChanged()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(qrData -> {
          if (scanListener != null) {
            scanListener.onQrDataFound(qrData);
          }
        });

    lifecycleDisposable.add(qrDisposable);

    return container;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    MenuItem switchCamera = ((DeviceActivity) requireActivity()).getCameraSwitchItem();

    if (switchCamera != null) {
      switchCamera.setVisible(true);
      switchCamera.setOnMenuItemClickListener(v -> {
        scannerView.toggleCamera();
        return true;
      });
    }
  }

  public ImageView getDevicesImage() {
    return devicesImage;
  }

  public void setScanListener(ScanListener scanListener) {
    this.scanListener = scanListener;
  }
}
