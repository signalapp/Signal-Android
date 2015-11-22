package org.thoughtcrime.securesms.jobs;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.NotFoundException;
import org.whispersystems.textsecure.api.push.exceptions.PushNetworkException;

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
    TextSecureMessageSender textSecureMessageSender = mock(TextSecureMessageSender.class);
    long                    timestamp               = System.currentTimeMillis();

    DeliveryReceiptJob deliveryReceiptJob = new DeliveryReceiptJob(context,
                                                                   "+14152222222",
                                                                   timestamp, "foo");

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule(textSecureMessageSender));
    objectGraph.inject(deliveryReceiptJob);

    deliveryReceiptJob.onRun();

    ArgumentCaptor<TextSecureAddress> captor = ArgumentCaptor.forClass(TextSecureAddress.class);
    verify(textSecureMessageSender).sendDeliveryReceipt(captor.capture(), eq(timestamp));

    assertTrue(captor.getValue().getRelay().get().equals("foo"));
    assertTrue(captor.getValue().getNumber().equals("+14152222222"));
  }

  public void testNetworkError() throws IOException {
    TextSecureMessageSender textSecureMessageSender = mock(TextSecureMessageSender.class);
    long                    timestamp               = System.currentTimeMillis();

    Mockito.doThrow(new PushNetworkException("network error"))
           .when(textSecureMessageSender)
           .sendDeliveryReceipt(any(TextSecureAddress.class), eq(timestamp));


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
           .sendDeliveryReceipt(any(TextSecureAddress.class), eq(timestamp));

    try {
      deliveryReceiptJob.onRun();
      throw new AssertionError();
    } catch (IOException e) {
      assertFalse(deliveryReceiptJob.onShouldRetry(e));
    }
  }

  @Module(injects = DeliveryReceiptJob.class)
  public static class TestModule {

    private final TextSecureMessageSender textSecureMessageSender;

    public TestModule(TextSecureMessageSender textSecureMessageSender) {
      this.textSecureMessageSender = textSecureMessageSender;
    }

    @Provides
    TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
      return new TextSecureMessageSenderFactory() {
        @Override
        public TextSecureMessageSender create() {
          return textSecureMessageSender;
        }
      };
    }
  }

}
