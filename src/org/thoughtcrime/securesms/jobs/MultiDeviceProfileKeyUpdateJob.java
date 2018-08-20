package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

public class MultiDeviceProfileKeyUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;
  private static final String TAG = MultiDeviceProfileKeyUpdateJob.class.getSimpleName();

  @Inject transient SignalServiceMessageSender messageSender;

  public MultiDeviceProfileKeyUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withGroupId(MultiDeviceProfileKeyUpdateJob.class.getSimpleName())
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(getContext())) {
      Log.w(TAG, "Not multi device...");
      return;
    }

    Optional<byte[]>           profileKey = Optional.of(ProfileKeyUtil.getProfileKey(getContext()));
    ByteArrayOutputStream      baos       = new ByteArrayOutputStream();
    DeviceContactsOutputStream out        = new DeviceContactsOutputStream(baos);

    out.write(new DeviceContact(TextSecurePreferences.getLocalNumber(getContext()),
                                Optional.absent(),
                                Optional.absent(),
                                Optional.absent(),
                                Optional.absent(),
                                profileKey, false, Optional.absent()));

    out.close();

    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                                                                            .withStream(new ByteArrayInputStream(baos.toByteArray()))
                                                                            .withContentType("application/octet-stream")
                                                                            .withLength(baos.toByteArray().length)
                                                                            .build();

    SignalServiceSyncMessage      syncMessage      = SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, false));

    messageSender.sendMessage(syncMessage);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Profile key sync failed!");
  }
}
