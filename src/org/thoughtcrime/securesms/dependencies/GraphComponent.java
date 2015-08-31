package org.thoughtcrime.securesms.dependencies;

import org.thoughtcrime.securesms.DeviceListActivity.DeviceListFragment;
import org.thoughtcrime.securesms.jobs.DeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.PushReceivedJob;
import org.thoughtcrime.securesms.jobs.PushSendJob;
import org.thoughtcrime.securesms.jobs.TextSecureJob;
import org.thoughtcrime.securesms.service.MessageRetrievalService;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {TextSecureCommunicationModule.class,
                      AxolotlStorageModule.class})
public interface GraphComponent {
  void inject(TextSecureJob injectable);
  void inject(DeliveryReceiptJob injectable);
  void inject(PushSendJob injectable);
  void inject(PushReceivedJob injectable);

  void inject(MessageRetrievalService injectable);
  void inject(DeviceListFragment injectable);
}
