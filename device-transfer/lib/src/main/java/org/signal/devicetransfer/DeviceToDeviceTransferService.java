package org.signal.devicetransfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service to help manage interactions with the {@link DeviceTransferClient} and
 * {@link DeviceTransferServer}.
 */
public class DeviceToDeviceTransferService extends Service implements ShutdownCallback {

  private static final String TAG = Log.tag(DeviceToDeviceTransferService.class);

  private static final String ACTION_START_SERVER = "start";
  private static final String ACTION_START_CLIENT = "start_client";
  private static final String ACTION_SET_VERIFIED = "set_verified";
  private static final String ACTION_STOP         = "stop";

  private static final String EXTRA_PENDING_INTENT = "extra_pending_intent";
  private static final String EXTRA_TASK           = "extra_task";
  private static final String EXTRA_NOTIFICATION   = "extra_notification_data";
  private static final String EXTRA_IS_VERIFIED    = "is_verified";

  private TransferNotificationData notificationData;
  private PendingIntent            pendingIntent;
  private DeviceTransferServer     server;
  private DeviceTransferClient     client;
  private PowerManager.WakeLock    wakeLock;

  public static void startServer(@NonNull Context context,
                                 @NonNull ServerTask serverTask,
                                 @NonNull TransferNotificationData transferNotificationData,
                                 @Nullable PendingIntent pendingIntent)
  {
    Intent intent = new Intent(context, DeviceToDeviceTransferService.class);
    intent.setAction(ACTION_START_SERVER)
          .putExtra(EXTRA_TASK, serverTask)
          .putExtra(EXTRA_NOTIFICATION, transferNotificationData)
          .putExtra(EXTRA_PENDING_INTENT, pendingIntent);

    context.startService(intent);
  }

  public static void startClient(@NonNull Context context,
                                 @NonNull ClientTask clientTask,
                                 @NonNull TransferNotificationData transferNotificationData,
                                 @Nullable PendingIntent pendingIntent)
  {
    Intent intent = new Intent(context, DeviceToDeviceTransferService.class);
    intent.setAction(ACTION_START_CLIENT)
          .putExtra(EXTRA_TASK, clientTask)
          .putExtra(EXTRA_NOTIFICATION, transferNotificationData)
          .putExtra(EXTRA_PENDING_INTENT, pendingIntent);

    context.startService(intent);
  }

  public static void setAuthenticationCodeVerified(@NonNull Context context, boolean verified) {
    Intent intent = new Intent(context, DeviceToDeviceTransferService.class);
    intent.setAction(ACTION_SET_VERIFIED)
          .putExtra(EXTRA_IS_VERIFIED, verified);

    context.startService(intent);
  }

  public static void stop(@NonNull Context context) {
    context.startService(new Intent(context, DeviceToDeviceTransferService.class).setAction(ACTION_STOP));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.v(TAG, "onCreate");

    EventBus.getDefault().register(this);
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventMainThread(@NonNull TransferStatus event) {
    updateNotification(event);
  }

  @Override
  public void onDestroy() {
    Log.v(TAG, "onDestroy");

    EventBus.getDefault().unregister(this);

    if (client != null) {
      client.stop();
      client = null;
    }

    if (server != null) {
      server.stop();
      server = null;
    }

    if (wakeLock != null) {
      wakeLock.release();
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
      shutdown();
      return START_NOT_STICKY;
    }

    Log.d(TAG, "Action: " + action);
    switch (action) {
      case ACTION_START_SERVER: {
        if (server == null) {
          notificationData = intent.getParcelableExtra(EXTRA_NOTIFICATION);
          pendingIntent    = intent.getParcelableExtra(EXTRA_PENDING_INTENT);
          server           = new DeviceTransferServer(getApplicationContext(),
                                                      (ServerTask) Objects.requireNonNull(intent.getSerializableExtra(EXTRA_TASK)),
                                                      this);
          acquireWakeLock();
          server.start();
        } else {
          Log.i(TAG, "Can't start server, already started.");
        }
        break;
      }
      case ACTION_START_CLIENT: {
        if (client == null) {
          notificationData = intent.getParcelableExtra(EXTRA_NOTIFICATION);
          pendingIntent    = intent.getParcelableExtra(EXTRA_PENDING_INTENT);
          client           = new DeviceTransferClient(getApplicationContext(),
                                                      (ClientTask) Objects.requireNonNull(intent.getSerializableExtra(EXTRA_TASK)),
                                                      this);
          acquireWakeLock();
          client.start();
        } else {
          Log.i(TAG, "Can't start client, client already started.");
        }
        break;
      }
      case ACTION_SET_VERIFIED:
        boolean isVerified = intent.getBooleanExtra(EXTRA_IS_VERIFIED, false);
        if (server != null) {
          server.setVerified(isVerified);
        } else if (client != null) {
          client.setVerified(isVerified);
        }
        break;
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

  private void acquireWakeLock() {
    if (wakeLock == null) {
      PowerManager powerManager = ContextCompat.getSystemService(this, PowerManager.class);
      if (powerManager != null) {
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "signal:d2dpartial");
      }
    }

    if (!wakeLock.isHeld()) {
      wakeLock.acquire(TimeUnit.HOURS.toMillis(2));
    }
  }

  private void updateNotification(@NonNull TransferStatus transferStatus) {
    if (notificationData != null && (client != null || server != null)) {
      startForeground(notificationData.notificationId, createNotification(transferStatus, notificationData));
    }
  }

  private @NonNull Notification createNotification(@NonNull TransferStatus transferStatus, @NonNull TransferNotificationData notificationData) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationData.channelId);

    String contentText = "";
    switch (transferStatus.getTransferMode()) {
      case READY:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_ready);
        break;
      case STARTING_UP:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_starting_up);
        break;
      case DISCOVERY:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_discovery);
        break;
      case NETWORK_CONNECTED:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_network_connected);
        break;
      case VERIFICATION_REQUIRED:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_verification_required);
        break;
      case SERVICE_CONNECTED:
        contentText = getString(R.string.DeviceToDeviceTransferService_status_service_connected);
        break;
      case UNAVAILABLE:
      case FAILED:
      case SERVICE_DISCONNECTED:
      case SHUTDOWN:
        Log.d(TAG, "Intentionally no notification text for: " + transferStatus.getTransferMode());
        break;
      default:
        throw new AssertionError("No notification text for: " + transferStatus.getTransferMode());
    }

    builder.setSmallIcon(notificationData.icon)
           .setOngoing(true)
           .setContentTitle(getString(R.string.DeviceToDeviceTransferService_content_title))
           .setContentText(contentText)
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
