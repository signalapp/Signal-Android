package org.whispersystems.signalservice.api.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.SubscriberId;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class DonationsServiceTest {

  private final PushServiceSocket pushServiceSocket = Mockito.mock(PushServiceSocket.class);
  private final DonationsService  testSubject       = new DonationsService(pushServiceSocket);

  @Test
  public void givenASubscriberId_whenIGetASuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndNonEmptyObject() throws Exception {
    // GIVEN
    SubscriberId subscriberId = SubscriberId.generate();
    when(pushServiceSocket.getSubscription(subscriberId.serialize()))
        .thenReturn(getActiveSubscription());

    // WHEN
    ServiceResponse<ActiveSubscription> response = testSubject.getSubscription(subscriberId);

    // THEN
    verify(pushServiceSocket).getSubscription(subscriberId.serialize());
    assertEquals(200, response.getStatus());
    assertTrue(response.getResult().isPresent());
  }

  @Test
  public void givenASubscriberId_whenIGetAnUnsuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndEmptyObject() throws Exception {
    // GIVEN
    SubscriberId subscriberId = SubscriberId.generate();
    when(pushServiceSocket.getSubscription(subscriberId.serialize()))
        .thenThrow(new NonSuccessfulResponseCodeException(403));

    // WHEN
    ServiceResponse<ActiveSubscription> response = testSubject.getSubscription(subscriberId);

    // THEN
    verify(pushServiceSocket).getSubscription(subscriberId.serialize());
    assertEquals(403, response.getStatus());
    assertFalse(response.getResult().isPresent());
  }

  private ActiveSubscription getActiveSubscription() {
    return ActiveSubscription.EMPTY;
  }
}
