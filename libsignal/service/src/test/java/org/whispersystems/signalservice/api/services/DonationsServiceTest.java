package org.whispersystems.signalservice.api.services;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.SubscriberId;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class DonationsServiceTest {

  private static final TestScheduler TEST_SCHEDULER = new TestScheduler();

  private final PushServiceSocket pushServiceSocket = Mockito.mock(PushServiceSocket.class);
  private final DonationsService  testSubject       = new DonationsService(pushServiceSocket);

  @BeforeClass
  public static void setUpClass() {
    RxJavaPlugins.setIoSchedulerHandler(scheduler -> TEST_SCHEDULER);
  }

  @Test
  public void givenASubscriberId_whenIGetASuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndNonEmptyObject() throws Exception {
    // GIVEN
    SubscriberId subscriberId = SubscriberId.generate();
    when(pushServiceSocket.getSubscription(subscriberId.serialize()))
        .thenReturn(getActiveSubscription());

    // WHEN
    TestObserver<ServiceResponse<ActiveSubscription>> testObserver = testSubject.getSubscription(subscriberId).test();

    // THEN
    TEST_SCHEDULER.triggerActions();
    verify(pushServiceSocket).getSubscription(subscriberId.serialize());
    testObserver.assertComplete().assertValue(value -> value.getStatus() == 200 && value.getResult().isPresent());
  }

  @Test
  public void givenASubscriberId_whenIGetAnUnsuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndEmptyObject() throws Exception {
    // GIVEN
    SubscriberId subscriberId = SubscriberId.generate();
    when(pushServiceSocket.getSubscription(subscriberId.serialize()))
        .thenThrow(new NonSuccessfulResponseCodeException(403));

    // WHEN
    TestObserver<ServiceResponse<ActiveSubscription>> testObserver = testSubject.getSubscription(subscriberId).test();

    // THEN
    TEST_SCHEDULER.triggerActions();
    verify(pushServiceSocket).getSubscription(subscriberId.serialize());
    testObserver.assertComplete().assertValue(value -> value.getStatus() == 403 && !value.getResult().isPresent());
  }

  private ActiveSubscription getActiveSubscription() {
    return new ActiveSubscription(null);
  }
}
