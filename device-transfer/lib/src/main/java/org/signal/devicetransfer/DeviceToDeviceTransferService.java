package org.signal.devicetransfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.util.Objects;

/**
 * Foreground service to help manage interactions with the {@link DeviceTransferClient} and
 * {@link DeviceTransferServer}.
 */
public class DeviceToDeviceTransferService extends Service implements ShutdownCallback {

  private static final String TAG = Log.tag(DeviceToDeviceTransferService.class);

  private static final int INVALID_PORT = -1;

  private static final String ACTION_START_SERVER = "start";
  private static final String ACTION_START_CLIENT = "start_client";
  private static final String ACTION_STOP         = "stop";

  private static final String EXTRA_PENDING_INTENT = "extra_pending_intent";
  private static final String EXTRA_TASK           = "extra_task";
  private static final String EXTRA_NOTIFICATION   = "extra_notification_data";
  private static final String EXTRA_PORT           = "extra_port";

  private TransferNotificationData notificationData;
  private PendingIntent            pendingIntent;
  private DeviceTransferServer     server;
  private DeviceTransferClient     client;

  public static void startServer(@NonNull Context context,
                                 int port,
                                 @NonNull ServerTask serverTask,
                                 @NonNull TransferNotificationData transferNotificationData,
                                 @Nullable PendingIntent pendingIntent)
  {
    Intent intent = new Intent(context, DeviceToDeviceTransferService.class);
    intent.setAction(ACTION_START_SERVER)
          .putExtra(EXTRA_TASK, serverTask)
          .putExtra(EXTRA_PORT, port)
          .putExtra(EXTRA_NOTIFICATION, transferNotificationData)
          .putExtra(EXTRA_PENDING_INTENT, pendingIntent);

    context.startService(intent);
  }

  public static void startClient(@NonNull Context context,
                                 int port,
                                 @NonNull ClientTask clientTask,
                                 @NonNull TransferNotificationData transferNotificationData,
                                 @Nullable PendingIntent pendingIntent)
  {
    Intent intent = new Intent(context, DeviceToDeviceTransferService.class);
    intent.setAction(ACTION_START_CLIENT)
          .putExtra(EXTRA_TASK, clientTask)
          .putExtra(EXTRA_PORT, port)
          .putExtra(EXTRA_NOTIFICATION, transferNotificationData)
          .putExtra(EXTRA_PENDING_INTENT, pendingIntent);

    context.startService(intent);
  }

  public static void stop(@NonNull Context context) {
    context.startService(new Intent(context, DeviceToDeviceTransferService.class).setAction(ACTION_STOP));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.e(TAG, "onCreate");

    EventBus.getDefault().register(this);
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventMainThread(@NonNull TransferMode event) {
    updateNotification(event);
  }

  private void update(@NonNull TransferMode transferMode) {
    EventBus.getDefault().postSticky(transferMode);
  }

  @Override
  public void onDestroy() {
    Log.e(TAG, "onDestroy");

    EventBus.getDefault().unregister(this);

    if (client != null) {
      client.shutdown();
      client = null;
    }

    if (server != null) {
      server.shutdown();
      server = null;
    }

    super.onDestroy();
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (intent == null) {
      return START_NOT_STICKY;
    }

    final String action = intent.getAction();
    if (action == null) {
      return START_NOT_STICKY;
    }

    final WifiDirect.AvailableStatus availability = WifiDirect.getAvailability(this);
    if (availability != WifiDirect.AvailableStatus.AVAILABLE) {
      update(availability == WifiDirect.AvailableStatus.FINE_LOCATION_PERMISSION_NOT_GRANTED ? TransferMode.PERMISSIONS
                                                                                             : TransferMode.UNAVAILABLE);
      shutdown();
      return START_NOT_STICKY;
    }

    switch (action) {
      case ACTION_START_SERVER: {
        int port = intent.getIntExtra(EXTRA_PORT, INVALID_PORT);
        if (server == null && port != -1) {
          notificationData = intent.getParcelableExtra(EXTRA_NOTIFICATION);
          pendingIntent    = intent.getParcelableExtra(EXTRA_PENDING_INTENT);
          server           = new DeviceTransferServer(getApplicationContext(),
                                                      (ServerTask) Objects.requireNonNull(intent.getSerializableExtra(EXTRA_TASK)),
                                                      port,
                                                      this);
          updateNotification(TransferMode.READY);
          server.start();
        } else {
          Log.i(TAG, "Can't start server. already_started: " + (server != null) + " port: " + port);
        }
        break;
      }
      case ACTION_START_CLIENT: {
        int port = intent.getIntExtra(EXTRA_PORT, INVALID_PORT);
        if (client == null && port != -1) {
          notificationData = intent.getParcelableExtra(EXTRA_NOTIFICATION);
          pendingIntent    = intent.getParcelableExtra(EXTRA_PENDING_INTENT);
          client           = new DeviceTransferClient(getApplicationContext(),
                                                      (ClientTask) Objects.requireNonNull(intent.getSerializableExtra(EXTRA_TASK)),
                                                      port,
                                                      this);
          updateNotification(TransferMode.READY);
          client.start();
        } else {
          Log.i(TAG, "Can't start client. already_started: " + (client != null) + " port: " + port);
        }
        break;
      }
      case ACTION_STOP:
        shutdown();
        break;
    }

    return START_STICKY;
  }

  @Override
  public void shutdown() {
    Log.i(TAG, "Shutdown");
    ThreadUtil.runOnMain(() -> {
      stopForeground(true);
      stopSelf();
    });
  }

  private void updateNotification(@NonNull TransferMode transferMode) {
    if (notificationData != null && (client != null || server != null)) {
      startForeground(notificationData.notificationId, createNotification(transferMode, notificationData));
    }
  }

  private @NonNull Notification createNotification(@NonNull TransferMode transferMode, @NonNull TransferNotificationData notificationData) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationData.channelId);

    //TODO [cody] build notification to spec
    builder.setSmallIcon(notificationData.icon)
           .setOngoing(true)
           .setContentTitle("Device Transfer")
           .setContentText("Status: " + transferMode.name())
           .setContentIntent(pendingIntent);

    return builder.build();
  }

  @Override
  public @Nullable IBinder onBind(@NonNull Intent intent) {
    throw new UnsupportedOperationException();
  }

  public static class TransferNotificationData implements Parcelable {
    private final int    notificationId;
    private final String channelId;
    private final int    icon;

    public TransferNotificationData(int notificationId, @NonNull String channelId, int icon) {
      this.notificationId = notificationId;
      this.channelId      = channelId;
      this.icon           = icon;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
      dest.writeInt(notificationId);
      dest.writeString(channelId);
      dest.writeInt(icon);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    public static final Creator<TransferNotificationData> CREATOR = new Creator<TransferNotificationData>() {
      @Override
      public @NonNull TransferNotificationData createFromParcel(@NonNull Parcel in) {
        return new TransferNotificationData(in.readInt(), in.readString(), in.readInt());
      }

      @Override
      public @NonNull TransferNotificationData[] newArray(int size) {
        return new TransferNotificationData[size];
      }
    };
  }
}
