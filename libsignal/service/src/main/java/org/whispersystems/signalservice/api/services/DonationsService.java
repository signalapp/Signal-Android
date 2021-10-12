package org.whispersystems.signalservice.api.services;

import org.signal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.EmptyResponse;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.DonationIntentResult;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.io.IOException;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One-stop shop for Signal service calls related to donations.
 */
public class DonationsService {
  private final PushServiceSocket pushServiceSocket;

  public DonationsService(
      SignalServiceConfiguration configuration,
      CredentialsProvider credentialsProvider,
      String signalAgent,
      GroupsV2Operations groupsV2Operations,
      boolean automaticNetworkRetry
  ) {
    this.pushServiceSocket  = new PushServiceSocket(configuration, credentialsProvider, signalAgent, groupsV2Operations.getProfileOperations(), automaticNetworkRetry);
  }

  /**
   * Allows a user to redeem a given receipt they were given after submitting a donation successfully.
   *
   * @param receiptCredentialPresentation Receipt
   * @param visible                       Whether the badge will be visible on the user's profile immediately after redemption
   * @param primary                       Whether the badge will be made primary immediately after redemption
   */
  public Single<ServiceResponse<EmptyResponse>> redeemReceipt(ReceiptCredentialPresentation receiptCredentialPresentation, boolean visible, boolean primary) {
    return Single.fromCallable(() -> {
      try {
        pushServiceSocket.redeemDonationReceipt(receiptCredentialPresentation, visible, primary);
        return ServiceResponse.forResult(EmptyResponse.INSTANCE, 200, null);
      } catch (Exception e) {
        return ServiceResponse.<EmptyResponse>forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Submits price information to the server to generate a payment intent via the payment gateway.
   *
   * @param amount        Price, in the minimum currency unit (e.g. cents or yen)
   * @param currencyCode  The currency code for the amount
   * @return              A ServiceResponse containing a DonationIntentResult with details given to us by the payment gateway.
   */
  public Single<ServiceResponse<DonationIntentResult>> createDonationIntentWithAmount(String amount, String currencyCode) {
    return Single.fromCallable(() -> {
      try {
        return ServiceResponse.forResult(this.pushServiceSocket.createDonationIntentWithAmount(amount, currencyCode), 200, null);
      } catch (IOException e) {
        return ServiceResponse.<DonationIntentResult>forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }
}
