package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;

import org.session.libsession.messaging.jobs.Data;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.threads.GroupRecord;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsession.utilities.GroupUtil;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.SignalServiceMessageSender;
import org.session.libsignal.service.api.crypto.UntrustedIdentityException;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceGroup.Type;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class PushGroupUpdateJob extends BaseJob implements InjectableType {

  public static final String KEY = "PushGroupUpdateJob";

  private static final String TAG = PushGroupUpdateJob.class.getSimpleName();

  private static final String KEY_SOURCE   = "source";
  private static final String KEY_GROUP_ID = "group_id";

  @Inject SignalServiceMessageSender messageSender;

  private String source;
  private byte[] groupId;

  public PushGroupUpdateJob(String source, byte[] groupId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
        source,
        groupId);
  }

  private PushGroupUpdateJob(@NonNull Job.Parameters parameters, String source, byte[] groupId) {
    super(parameters);

    this.source  = source;
    this.groupId = groupId;
  }

  @Override
  public @NonNull
  Data serialize() {
    return new Data.Builder().putString(KEY_SOURCE, source)
                             .putString(KEY_GROUP_ID, GroupUtil.getEncodedClosedGroupID(groupId))
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    GroupDatabase           groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord>   record        = groupDatabase.getGroup(GroupUtil.getEncodedClosedGroupID(groupId));
    SignalServiceAttachment avatar        = null;

    if (record == null) {
      Log.w(TAG, "No information for group record info request: " + new String(groupId));
      return;
    }

    if (record.get().getAvatar() != null) {
      avatar = SignalServiceAttachmentStream.newStreamBuilder()
                                            .withContentType("image/jpeg")
                                            .withStream(new ByteArrayInputStream(record.get().getAvatar()))
                                            .withLength(record.get().getAvatar().length)
                                            .build();
    }

    List<String> members = new LinkedList<>();
    for (Address member : record.get().getMembers()) {
      members.add(member.serialize());
    }

    List<String> admins = new LinkedList<>();
    for (Address admin : record.get().getAdmins()) {
      admins.add(admin.serialize());
    }

    SignalServiceGroup groupContext = SignalServiceGroup.newBuilder(Type.UPDATE)
                                                        .withAvatar(avatar)
                                                        .withId(groupId, SignalServiceGroup.GroupType.SIGNAL)
                                                        .withMembers(members)
                                                        .withAdmins(admins)
                                                        .withName(record.get().getTitle())
                                                        .build();

    Address   groupAddress   = Address.Companion.fromSerialized(GroupUtil.getEncodedClosedGroupID(groupId));
    Recipient groupRecipient = Recipient.from(context, groupAddress, false);

    SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                                                               .asGroupMessage(groupContext)
                                                               .withTimestamp(System.currentTimeMillis())
                                                               .withExpiration(groupRecipient.getExpireMessages())
                                                               .build();

    messageSender.sendMessage(0, new SignalServiceAddress(source),
                              UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.Companion.fromSerialized(source), false)),
                              message);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {

  }

  public static final class Factory implements Job.Factory<PushGroupUpdateJob> {
    @Override
    public @NonNull PushGroupUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushGroupUpdateJob(parameters,
                                    data.getString(KEY_SOURCE),
                                    GroupUtil.getDecodedGroupIDAsData(data.getString(KEY_GROUP_ID)));
    }
  }
}
