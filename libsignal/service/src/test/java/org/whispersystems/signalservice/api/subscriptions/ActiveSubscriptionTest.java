package org.whispersystems.signalservice.api.subscriptions;

import org.junit.Test;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActiveSubscriptionTest {
  @Test
  public void givenActiveSubscription_whenIIsPaymentFailure_thenIExpectFalse() throws Exception {
    String             input              = "{\"subscription\":{\"level\":2000,\"billingCycleAnchor\":1636124746.000000000,\"endOfCurrentPeriod\":1675609546.000000000,\"active\":true,\"cancelAtPeriodEnd\":false,\"currency\":\"USD\",\"amount\":2000,\"status\":\"active\"},\"chargeFailure\":null}";
    ActiveSubscription activeSubscription = JsonUtil.fromJson(input, ActiveSubscription.class);

    assertTrue(activeSubscription.isActive());
    assertFalse(activeSubscription.isFailedPayment());
  }
}