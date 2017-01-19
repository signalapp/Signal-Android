package org.thoughtcrime.securesms.jobs;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DeliveryReceiptJobTest extends BaseUnitTest {
  @Test
  public void testDelivery() throws IOException {
    SignalServiceMessageSender textSecureMessageSender = mock(SignalServiceMessageSender.class);
    long                    timestamp               = System.currentTimeMillis();

    DeliveryReceiptJob deliveryReceiptJob = new DeliveryReceiptJob(context,
                                                                   "+14152222222",
                                                                   timestamp, "foo");

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule(textSecureMessageSender));
    objectGraph.inject(deliveryReceiptJob);

    deliveryReceiptJob.onRun();

    ArgumentCaptor<SignalServiceAddress> captor = ArgumentCaptor.forClass(SignalServiceAddress.class);
    verify(textSecureMessageSender).sendDeliveryReceipt(captor.capture(), eq(timestamp));

    assertTrue(captor.getValue().getRelay().get().equals("foo"));
    assertTrue(captor.getValue().getNumber().equals("+14152222222"));
  }

  @Test
  public void testNetworkError() throws IOException {
    SignalServiceMessageSender textSecureMessageSender = mock(SignalServiceMessageSender.class);
    long                    timestamp               = System.currentTimeMillis();

    Mockito.doThrow(new PushNetworkException("network error"))
           .when(textSecureMessageSender)
           .sendDeliveryReceipt(any(SignalServiceAddress.class), eq(timestamp));


    DeliveryReceiptJob deliveryReceiptJob = new DeliveryReceiptJob(context,
                                                                   "+14152222222",
                                                                   timestamp, "foo");

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule(textSecureMessageSender));
    objectGraph.inject(deliveryReceiptJob);

    try {
      deliveryReceiptJob.onRun();
      throw new AssertionError();
    } catch (IOException e) {
      assertTrue(deliveryReceiptJob.onShouldRetry(e));
    }

    Mockito.doThrow(new NotFoundException("not found"))
           .when(textSecureMessageSender)
           .sendDeliveryReceipt(any(SignalServiceAddress.class), eq(timestamp));

    try {
      deliveryReceiptJob.onRun();
      throw new AssertionError();
    } catch (IOException e) {
      assertFalse(deliveryReceiptJob.onShouldRetry(e));
    }
  }

  @Module(injects = DeliveryReceiptJob.class)
  public static class TestModule {

    private final SignalServiceMessageSender textSecureMessageSender;

    public TestModule(SignalServiceMessageSender textSecureMessageSender) {
      this.textSecureMessageSender = textSecureMessageSender;
    }

    @Provides
    SignalMessageSenderFactory provideSignalServiceMessageSenderFactory() {
      return new SignalMessageSenderFactory() {
        @Override
        public SignalServiceMessageSender create() {
          return textSecureMessageSender;
        }
      };
    }
  }

}
