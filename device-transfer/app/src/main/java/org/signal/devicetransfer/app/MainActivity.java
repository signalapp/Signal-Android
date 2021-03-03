package org.signal.devicetransfer.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.devicetransfer.ClientTask;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.DeviceToDeviceTransferService.TransferNotificationData;
import org.signal.devicetransfer.ServerTask;
import org.signal.devicetransfer.TransferMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

  private static final String TRANSFER_NOTIFICATION_CHANNEL = "DEVICE_TO_DEVICE_TRANSFER";

  private LinearLayout list;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (Build.VERSION.SDK_INT > 26) {
      NotificationChannel deviceTransfer = new NotificationChannel(TRANSFER_NOTIFICATION_CHANNEL, "Device Transfer", NotificationManager.IMPORTANCE_DEFAULT);
      NotificationManagerCompat.from(this).createNotificationChannel(deviceTransfer);
    }

    list = findViewById(R.id.list);

    final TransferNotificationData data = new TransferNotificationData(1337,
                                                                       TRANSFER_NOTIFICATION_CHANNEL,
                                                                       R.drawable.ic_refresh_20);

    findViewById(R.id.start_server).setOnClickListener(v -> {
      DeviceToDeviceTransferService.startServer(this,
                                                8888,
                                                new ServerReceiveRandomBytes(),
                                                data,
                                                PendingIntent.getActivity(this,
                                                                          0,
                                                                          new Intent(this, MainActivity.class),
                                                                          0));

      list.removeAllViews();
    });

    findViewById(R.id.start_client).setOnClickListener(v -> {
      DeviceToDeviceTransferService.startClient(this,
                                                8888,
                                                new ClientSendRandomBytes(),
                                                data,
                                                PendingIntent.getActivity(this,
                                                                          0,
                                                                          new Intent(this, MainActivity.class),
                                                                          0));

      list.removeAllViews();
    });

    findViewById(R.id.stop).setOnClickListener(v -> {
      DeviceToDeviceTransferService.stop(this);
    });

    findViewById(R.id.enable_permission).setOnClickListener(v -> {
      if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 420);
      }
    });

    EventBus.getDefault().register(this);
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventMainThread(@NonNull TransferMode event) {
    TextView text = new TextView(this);
    text.setText(event.toString());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    text.setLayoutParams(params);
    list.addView(text);
  }

  private static class ClientSendRandomBytes implements ClientTask {

    private static final String TAG = "ClientSend";

    private final int rounds = 1000;

    @Override
    public void run(@NonNull Context context, @NonNull OutputStream outputStream) throws IOException {
      Random r    = new Random(System.currentTimeMillis());
      byte[] data = new byte[8192];
      r.nextBytes(data);

      long start = System.currentTimeMillis();
      Log.i(TAG, "Sending " + ((data.length * rounds) / 1024 / 1024) + "MB of random data!!!");
      for (int i = 0; i < rounds; i++) {
        outputStream.write(data);
        outputStream.flush();
      }
      long end = System.currentTimeMillis();
      Log.i(TAG, "Sending took: " + (end - start));
    }
  }

  private static class ServerReceiveRandomBytes implements ServerTask {

    private static final String TAG = "ServerReceive";

    @Override
    public void run(@NonNull Context context, @NonNull InputStream inputStream) throws IOException {
      long   start  = System.currentTimeMillis();
      byte[] data   = new byte[8192];
      int    result = 0;

      int i = 0;
      Log.i(TAG, "Start drinking from the fire hose!");
      while (result >= 0) {
        result = inputStream.read(data, 0, 8192);
        i++;
        if (i % 10000 == 0) {
          Log.i(TAG, "Round: " + i);
        }
      }
      long end = System.currentTimeMillis();
      Log.i(TAG, "Receive took: " + (end - start));
    }
  }
}
