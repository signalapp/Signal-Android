package org.thoughtcrime.securesms.jobs

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import org.signal.core.util.PendingIntentFlags.cancelCurrent
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.NewLinkedDeviceNotificationJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ServiceUtil
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Notifies users that a device has been linked to their account at a randomly time 1 - 3 hours after it was created
 */
class NewLinkedDeviceNotificationJob private constructor(
  private val data: NewLinkedDeviceNotificationJobData,
  parameters: Parameters
) : Job(parameters) {
  companion object {
    const val KEY: String = "NewLinkedDeviceNotificationJob"
    private val TAG = Log.tag(NewLinkedDeviceNotificationJob::class.java)

    @JvmStatic
    fun enqueue(deviceId: Int, deviceCreatedAt: Long) {
      AppDependencies.jobManager.add(NewLinkedDeviceNotificationJob(deviceId, deviceCreatedAt))
    }

    /**
     * Generates a random delay between 1 - 3 hours in milliseconds
     */
    private fun getRandomDelay(): Long {
      val delay = Random.nextInt(60, 180)
      return delay.minutes.inWholeMilliseconds
    }
  }

  constructor(
    deviceId: Int,
    deviceCreatedAt: Long
  ) : this(
    NewLinkedDeviceNotificationJobData(deviceId, deviceCreatedAt),
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setQueue("NewLinkedDeviceNotificationJob")
      .setInitialDelay(getRandomDelay())
      .setLifespan(7.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray = data.encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    if (NotificationChannels.getInstance().areNotificationsEnabled()) {
      val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.linkedDevices(context), cancelCurrent())
      val builder = NotificationCompat.Builder(context, NotificationChannels.getInstance().NEW_LINKED_DEVICE)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.NewLinkedDeviceNotification__you_linked_new_device))
        .setContentText(context.getString(R.string.NewLinkedDeviceNotification__a_new_device_was_linked, DateUtils.getDateTimeString(context, Locale.getDefault(), data.deviceCreatedAt)))
        .setContentIntent(pendingIntent)

      ServiceUtil.getNotificationManager(context).notify(NotificationIds.NEW_LINKED_DEVICE, builder.build())
    }
    SignalStore.misc.newLinkedDeviceId = data.deviceId
    SignalStore.misc.newLinkedDeviceCreatedTime = data.deviceCreatedAt
    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<NewLinkedDeviceNotificationJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): NewLinkedDeviceNotificationJob = NewLinkedDeviceNotificationJob(NewLinkedDeviceNotificationJobData.ADAPTER.decode(serializedData!!), parameters)
  }
}
