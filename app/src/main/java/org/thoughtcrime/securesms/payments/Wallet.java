package org.thoughtcrime.securesms.payments;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;
import com.mobilecoin.lib.AccountKey;
import com.mobilecoin.lib.AccountSnapshot;
import com.mobilecoin.lib.Amount;
import com.mobilecoin.lib.DefragmentationDelegate;
import com.mobilecoin.lib.MobileCoinClient;
import com.mobilecoin.lib.OwnedTxOut;
import com.mobilecoin.lib.PendingTransaction;
import com.mobilecoin.lib.Receipt;
import com.mobilecoin.lib.TokenId;
import com.mobilecoin.lib.Transaction;
import com.mobilecoin.lib.TxOutMemoBuilder;
import com.mobilecoin.lib.UnsignedLong;
import com.mobilecoin.lib.exceptions.AmountDecoderException;
import com.mobilecoin.lib.exceptions.AttestationException;
import com.mobilecoin.lib.exceptions.BadEntropyException;
import com.mobilecoin.lib.exceptions.FeeRejectedException;
import com.mobilecoin.lib.exceptions.FogReportException;
import com.mobilecoin.lib.exceptions.FogSyncException;
import com.mobilecoin.lib.exceptions.FragmentedAccountException;
import com.mobilecoin.lib.exceptions.InsufficientFundsException;
import com.mobilecoin.lib.exceptions.InvalidFogResponse;
import com.mobilecoin.lib.exceptions.InvalidReceiptException;
import com.mobilecoin.lib.exceptions.InvalidTransactionException;
import com.mobilecoin.lib.exceptions.InvalidUriException;
import com.mobilecoin.lib.exceptions.NetworkException;
import com.mobilecoin.lib.exceptions.SerializationException;
import com.mobilecoin.lib.exceptions.TransactionBuilderException;
import com.mobilecoin.lib.network.TransportProtocol;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.proto.MobileCoinLedger;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.util.Uint64RangeException;
import org.whispersystems.signalservice.api.util.Uint64Util;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class Wallet {

  private static final String TAG         = Log.tag(Wallet.class);
  private static final Object LEDGER_LOCK = new Object();

  private final MobileCoinConfig        mobileCoinConfig;
  private final MobileCoinClient        mobileCoinClient;
  private final AccountKey              account;
  private final MobileCoinPublicAddress publicAddress;

  private AccountSnapshot cachedAccountSnapshot;
  private Amount          cachedMinimumTxFee;

  public Wallet(@NonNull MobileCoinConfig mobileCoinConfig, @NonNull Entropy paymentsEntropy) {
    this.mobileCoinConfig = mobileCoinConfig;
    try {
      this.account       = AccountKey.fromBip39Entropy(paymentsEntropy.getBytes(), 0, mobileCoinConfig.getFogReportUri(), "", mobileCoinConfig.getFogAuthoritySpki());
      this.publicAddress = new MobileCoinPublicAddress(account.getPublicAddress());

      this.mobileCoinClient = new MobileCoinClient(account,
                                                   mobileCoinConfig.getFogUri(),
                                                   mobileCoinConfig.getConsensusUris(),
                                                   mobileCoinConfig.getConfig(),
                                                   TransportProtocol.forGRPC());
    } catch (InvalidUriException | BadEntropyException e) {
      throw new AssertionError(e);
    }
    try {
      reauthorizeClient();
    } catch (IOException e) {
      Log.w(TAG, "Failed to authorize client", e);
    }
  }

  public @NonNull MobileCoinPublicAddress getMobileCoinPublicAddress() {
    return publicAddress;
  }

  @AnyThread
  public @NonNull Balance getCachedBalance() {
    return SignalStore.paymentsValues().mobileCoinLatestBalance();
  }

  @AnyThread
  public @NonNull MobileCoinLedgerWrapper getCachedLedger() {
    return SignalStore.paymentsValues().mobileCoinLatestFullLedger();
  }

  @WorkerThread
  public @NonNull MobileCoinLedgerWrapper getFullLedger() {
    return getFullLedger(true);
  }

  @WorkerThread
  private @NonNull MobileCoinLedgerWrapper getFullLedger(boolean retryOnAuthFailure) {
    PaymentsValues paymentsValues = SignalStore.paymentsValues();
    try {
      MobileCoinLedgerWrapper ledger = tryGetFullLedger(null);

      paymentsValues.setMobileCoinFullLedger(Objects.requireNonNull(ledger));
    } catch (IOException | FogSyncException e) {
      if ((retryOnAuthFailure && e.getCause() instanceof NetworkException) &&
          (((NetworkException) e.getCause()).statusCode == 401))
      {
        Log.w(TAG, "Failed to get up to date ledger, due to temp auth failure, retrying", e);
        return getFullLedger(false);
      } else {
        Log.w(TAG, "Failed to get up to date ledger", e);
      }
    }

    return getCachedLedger();
  }

  /**
   * Retrieve a user owned ledger
   * @param minimumBlockIndex require the returned ledger to include all TxOuts to at least minimumBlockIndex
   * @return a wrapped MobileCoin ledger that contains only TxOuts owned by the AccountKey
   *         or null if the requested minimumBlockIndex cannot be retrieved
   */
  @WorkerThread
  public @Nullable MobileCoinLedgerWrapper tryGetFullLedger(@Nullable Long minimumBlockIndex) throws IOException, FogSyncException {
    try {
      MobileCoinLedger.Builder builder               = MobileCoinLedger.newBuilder();
      BigInteger               totalUnspent          = BigInteger.ZERO;
      long                     highestBlockTimeStamp = 0;
      UnsignedLong             highestBlockIndex     = UnsignedLong.ZERO;
      final long               asOfTimestamp         = System.currentTimeMillis();
      Amount                   minimumTxFee;
      AccountSnapshot          accountSnapshot;

      synchronized (LEDGER_LOCK) {
        minimumTxFee    = mobileCoinClient.getOrFetchMinimumTxFee(TokenId.MOB);
        accountSnapshot = mobileCoinClient.getAccountSnapshot();

        cachedMinimumTxFee = minimumTxFee;
        cachedAccountSnapshot = accountSnapshot;
      }

      if (minimumBlockIndex != null) {
        long snapshotBlockIndex = accountSnapshot.getBlockIndex().longValue();
        if (snapshotBlockIndex < minimumBlockIndex) {
          Log.d(TAG, "Waiting for block index");
          return null;
        }
      }

      for (OwnedTxOut txOut : accountSnapshot.getAccountActivity().getAllTokenTxOuts(TokenId.MOB)) {
        final Amount txOutAmount = txOut.getAmount();
        MobileCoinLedger.OwnedTXO.Builder txoBuilder = MobileCoinLedger.OwnedTXO.newBuilder()
                                                                                .setAmount(Uint64Util.bigIntegerToUInt64(txOutAmount.getValue()))
                                                                                .setReceivedInBlock(getBlock(txOut.getReceivedBlockIndex(), txOut.getReceivedBlockTimestamp()))
                                                                                .setKeyImage(ByteString.copyFrom(txOut.getKeyImage().getData()))
                                                                                .setPublicKey(ByteString.copyFrom(txOut.getPublicKey().getKeyBytes()));
        if (txOut.getSpentBlockIndex() != null &&
            (minimumBlockIndex == null || txOut.isSpent(UnsignedLong.valueOf(minimumBlockIndex))))
        {
          txoBuilder.setSpentInBlock(getBlock(txOut.getSpentBlockIndex(), txOut.getSpentBlockTimestamp()));
          builder.addSpentTxos(txoBuilder);
        } else {
          totalUnspent = totalUnspent.add(txOutAmount.getValue());
          builder.addUnspentTxos(txoBuilder);
        }

        if (txOut.getSpentBlockIndex() != null && txOut.getSpentBlockIndex().compareTo(highestBlockIndex) > 0) {
          highestBlockIndex = txOut.getSpentBlockIndex();
        }

        if (txOut.getReceivedBlockIndex().compareTo(highestBlockIndex) > 0) {
          highestBlockIndex = txOut.getReceivedBlockIndex();
        }

        if (txOut.getSpentBlockTimestamp() != null && txOut.getSpentBlockTimestamp().getTime() > highestBlockTimeStamp) {
          highestBlockTimeStamp = txOut.getSpentBlockTimestamp().getTime();
        }

        if (txOut.getReceivedBlockTimestamp() != null && txOut.getReceivedBlockTimestamp().getTime() > highestBlockTimeStamp) {
          highestBlockTimeStamp = txOut.getReceivedBlockTimestamp().getTime();
        }
      }
      builder.setBalance(Uint64Util.bigIntegerToUInt64(totalUnspent))
             .setTransferableBalance(Uint64Util.bigIntegerToUInt64(accountSnapshot.getTransferableAmount(minimumTxFee).getValue()))
             .setAsOfTimeStamp(asOfTimestamp)
             .setHighestBlock(MobileCoinLedger.Block.newBuilder()
                                                    .setBlockNumber(highestBlockIndex.longValue())
                                                    .setTimestamp(highestBlockTimeStamp));
      SignalStore.paymentsValues().setEnclaveFailure(false);
      return new MobileCoinLedgerWrapper(builder.build());
    } catch (InvalidFogResponse e) {
      Log.w(TAG, "Problem getting ledger", e);
      throw new IOException(e);
    } catch (NetworkException e) {
      Log.w(TAG, "Network problem getting ledger", e);
      if (e.statusCode == 401) {
        Log.d(TAG, "Reauthorizing client");
        reauthorizeClient();
      }
      throw new IOException(e);
    } catch (AttestationException e) {
      SignalStore.paymentsValues().setEnclaveFailure(true);
      Log.w(TAG, "Attestation problem getting ledger", e);
      throw new IOException(e);
    } catch (Uint64RangeException e) {
      throw new AssertionError(e);
    }
  }

  private static @Nullable MobileCoinLedger.Block getBlock(@NonNull UnsignedLong blockIndex, @Nullable Date timeStamp) throws Uint64RangeException {
    MobileCoinLedger.Block.Builder builder = MobileCoinLedger.Block.newBuilder();
    builder.setBlockNumber(Uint64Util.bigIntegerToUInt64(blockIndex.toBigInteger()));
    if (timeStamp != null) {
      builder.setTimestamp(timeStamp.getTime());
    }
    return builder.build();
  }

  @WorkerThread
  public @NonNull Money.MobileCoin getFee(@NonNull Money.MobileCoin amount) throws IOException {
    try {
      BigInteger      picoMob         = amount.requireMobileCoin().toPicoMobBigInteger();
      AccountSnapshot accountSnapshot = getCachedAccountSnapshot();
      Amount          minimumFee      = getCachedMinimumTxFee();
      Money.MobileCoin money;
      if (accountSnapshot != null && minimumFee != null) {
        money = Money.picoMobileCoin(accountSnapshot.estimateTotalFee(Amount.ofMOB(picoMob), minimumFee).getValue());
      } else {
        money = Money.picoMobileCoin(mobileCoinClient.estimateTotalFee(Amount.ofMOB(picoMob)).getValue());
      }
      SignalStore.paymentsValues().setEnclaveFailure(false);
      return money;
    } catch (AttestationException e) {
      SignalStore.paymentsValues().setEnclaveFailure(true);
      return Money.MobileCoin.ZERO;
    } catch (InvalidFogResponse | InsufficientFundsException e) {
      Log.w(TAG, "Failed to get fee", e);
      return Money.MobileCoin.ZERO;
    } catch (NetworkException  | FogSyncException e) {
      Log.w(TAG, "Failed to get fee", e);
      throw new IOException(e);
    }
  }

  @WorkerThread
  public @NonNull PaymentSubmissionResult sendPayment(@NonNull MobileCoinPublicAddress to,
                                                      @NonNull Money.MobileCoin amount,
                                                      @NonNull Money.MobileCoin totalFee)
  {
    List<TransactionSubmissionResult> transactionSubmissionResults = new LinkedList<>();
    sendPayment(to, amount, totalFee, false, transactionSubmissionResults);
    return new PaymentSubmissionResult(transactionSubmissionResults);
  }

  @WorkerThread
  public @NonNull TransactionStatusResult getSentTransactionStatus(@NonNull PaymentTransactionId transactionId) throws IOException, FogSyncException {
    try {
      PaymentTransactionId.MobileCoin mobcoinTransaction = (PaymentTransactionId.MobileCoin) transactionId;
      Transaction                     transaction        = Transaction.fromBytes(mobcoinTransaction.getTransaction());
      Transaction.Status              status             = mobileCoinClient.getTransactionStatusQuick(transaction);
      switch (status) {
        case UNKNOWN:
          Log.w(TAG, "Unknown sent Transaction Status");
          return TransactionStatusResult.inProgress();
        case FAILED:
          return TransactionStatusResult.failed();
        case ACCEPTED:
          return TransactionStatusResult.complete(status.getBlockIndex().longValue());
        default:
          throw new IllegalStateException("Unknown Transaction Status: " + status);
      }
    } catch (SerializationException e) {
      Log.w(TAG, e);
      return TransactionStatusResult.failed();
    } catch (NetworkException e) {
      Log.w(TAG, e);
      throw new IOException(e);
    }
  }

  @WorkerThread
  public @NonNull ReceivedTransactionStatus getReceivedTransactionStatus(@NonNull byte[] receiptBytes) throws IOException, FogSyncException {
    try {
      Receipt        receipt = Receipt.fromBytes(receiptBytes);
      Receipt.Status status  = mobileCoinClient.getReceiptStatus(receipt);
      ReceivedTransactionStatus txStatus = null;
      switch (status) {
        case UNKNOWN:
          Log.w(TAG, "Unknown received Transaction Status");
          txStatus = ReceivedTransactionStatus.inProgress();
          break;
        case FAILED:
          txStatus = ReceivedTransactionStatus.failed();
          break;
        case RECEIVED:
          final Amount amount = receipt.getAmountData(account);
          txStatus = ReceivedTransactionStatus.complete(Money.picoMobileCoin(amount.getValue()), status.getBlockIndex().longValue());
          break;
        default:
      }
      SignalStore.paymentsValues().setEnclaveFailure(false);
      if (txStatus == null) throw new IllegalStateException("Unknown Transaction Status: " + status);
      return txStatus;
    } catch (SerializationException | InvalidFogResponse | InvalidReceiptException e) {
      Log.w(TAG, e);
      return ReceivedTransactionStatus.failed();
    } catch (NetworkException e) {
      throw new IOException(e);
    } catch (AttestationException e) {
      SignalStore.paymentsValues().setEnclaveFailure(true);
      throw new IOException(e);
    } catch (AmountDecoderException e) {
      Log.w(TAG, "Failed to decode amount", e);
      return ReceivedTransactionStatus.failed();
    }
  }

  @WorkerThread
  private void sendPayment(@NonNull MobileCoinPublicAddress to,
                           @NonNull Money.MobileCoin amount,
                           @NonNull Money.MobileCoin totalFee,
                           boolean defragmentFirst,
                           @NonNull List<TransactionSubmissionResult> results)
  {
    Money.MobileCoin defragmentFees = Money.MobileCoin.ZERO;
    if (defragmentFirst) {
      try {
        defragmentFees = defragment(amount, results);
        SignalStore.paymentsValues().setEnclaveFailure(false);
      } catch (InsufficientFundsException e) {
        Log.w(TAG, "Insufficient funds", e);
        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.INSUFFICIENT_FUNDS, true));
        return;
      } catch (AttestationException e) {
        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, true));
        SignalStore.paymentsValues().setEnclaveFailure(true);
        return;
      } catch (TimeoutException | InvalidTransactionException | InvalidFogResponse | TransactionBuilderException | NetworkException | FogReportException | FogSyncException e) {
        Log.w(TAG, "Defragment failed", e);
        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, true));
        return;
      }
    }

    Money.MobileCoin   feeMobileCoin      = totalFee.subtract(defragmentFees).requireMobileCoin();
    BigInteger         picoMob            = amount.requireMobileCoin().toPicoMobBigInteger();
    PendingTransaction pendingTransaction = null;

    Log.i(TAG, String.format("Total fee advised: %s\nDefrag fees: %s\nTransaction fee: %s", totalFee, defragmentFees, feeMobileCoin));

    if (!feeMobileCoin.isPositive()) {
      Log.i(TAG, "No fee left after defrag");
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
      return;
    }

    try {
      AccountSnapshot accountSnapshot = getCachedAccountSnapshot();
      if (accountSnapshot != null) {
        pendingTransaction = accountSnapshot.prepareTransaction(to.getAddress(),
                                                                Amount.ofMOB(picoMob),
                                                                Amount.ofMOB(feeMobileCoin.toPicoMobBigInteger()),
                                                                TxOutMemoBuilder.createSenderAndDestinationRTHMemoBuilder(account));
      } else {
        pendingTransaction = mobileCoinClient.prepareTransaction(to.getAddress(),
                                                                 Amount.ofMOB(picoMob),
                                                                 Amount.ofMOB(feeMobileCoin.toPicoMobBigInteger()),
                                                                 TxOutMemoBuilder.createSenderAndDestinationRTHMemoBuilder(account));
      }
      SignalStore.paymentsValues().setEnclaveFailure(false);
    } catch (InsufficientFundsException e) {
      Log.w(TAG, "Insufficient funds", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.INSUFFICIENT_FUNDS, false));
    } catch (FeeRejectedException e) {
      Log.w(TAG, "Fee rejected " + totalFee, e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch (InvalidFogResponse | FogReportException e) {
      Log.w(TAG, "Invalid fog response", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch (FragmentedAccountException e) {
      if (defragmentFirst) {
        Log.w(TAG, "Account is fragmented, but already tried to defragment", e);
        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
      } else {
        Log.i(TAG, "Account is fragmented, defragmenting and retrying");
        sendPayment(to, amount, totalFee, true, results);
      }
    } catch (AttestationException e) {
      Log.w(TAG, "Attestation problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
      SignalStore.paymentsValues().setEnclaveFailure(true);
    } catch (NetworkException e) {
      Log.w(TAG, "Network problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch (TransactionBuilderException e) {
      Log.w(TAG, "Builder problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch(FogSyncException e) {
      Log.w(TAG, "Fog currently out of sync", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.NETWORK_FAILURE, false));
    }

    if (pendingTransaction == null) {
      Log.w(TAG, "Failed to create pending transaction");
      return;
    }

    try {
      Log.i(TAG, "Submitting transaction");
      mobileCoinClient.submitTransaction(pendingTransaction.getTransaction());
      Log.i(TAG, "Transaction submitted");
      results.add(TransactionSubmissionResult.successfullySubmitted(new PaymentTransactionId.MobileCoin(pendingTransaction.getTransaction().toByteArray(), pendingTransaction.getReceipt().toByteArray(), feeMobileCoin)));
      SignalStore.paymentsValues().setEnclaveFailure(false);
    } catch (NetworkException e) {
      Log.w(TAG, "Network problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.NETWORK_FAILURE, false));
    } catch (InvalidTransactionException e) {
      Log.w(TAG, "Invalid transaction", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch (AttestationException e) {
      Log.w(TAG, "Attestation problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
      SignalStore.paymentsValues().setEnclaveFailure(true);
    } catch (SerializationException e) {
      Log.w(TAG, "Serialization problem", e);
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    }
  }

  /**
   * Attempts to defragment the account. It will at most merge 16 UTXOs to 1.
   * Therefore it may need to be called more than once before a certain payment is possible.
   */
  @WorkerThread
  private @NonNull Money.MobileCoin defragment(@NonNull Money.MobileCoin amount, @NonNull List<TransactionSubmissionResult> results)
      throws TransactionBuilderException, NetworkException, InvalidTransactionException, AttestationException, FogReportException, InvalidFogResponse, TimeoutException, InsufficientFundsException, FogSyncException
  {
    Log.i(TAG, "Defragmenting account");
    DefragDelegate defragDelegate = new DefragDelegate(mobileCoinClient, results);
    mobileCoinClient.defragmentAccount(Amount.ofMOB(amount.toPicoMobBigInteger()), defragDelegate, true);
    Log.i(TAG, "Account defragmented at a cost of " + defragDelegate.totalFeesSpent);
    return defragDelegate.totalFeesSpent;
  }

  private void reauthorizeClient() throws IOException {
    AuthCredentials authorization = mobileCoinConfig.getAuth();
    mobileCoinClient.setFogBasicAuthorization(authorization.username(), authorization.password());
  }

  public void refresh() {
    getFullLedger();
  }

  /**
   * @return cached account snapshot or null if it's not available
   * @apiNote This method is synchronized with {@link #tryGetFullLedger}
   * to wait for an updated value if ledger update is in progress.
   */
  @WorkerThread
  private @Nullable AccountSnapshot getCachedAccountSnapshot() {
    synchronized (LEDGER_LOCK) {
      return cachedAccountSnapshot;
    }
  }

  /**
   * @return cached minimum transaction fee or null if it's not available
   * @apiNote This method is synchronized with {@link #tryGetFullLedger}
   * to wait for an updated value if ledger update is in progress.
   */
  @WorkerThread
  private @Nullable Amount getCachedMinimumTxFee() {
    synchronized (LEDGER_LOCK) {
      return cachedMinimumTxFee;
    }
  }

  public enum TransactionStatus {
    COMPLETE,
    IN_PROGRESS,
    FAILED
  }

  public static final class TransactionStatusResult {
    private final TransactionStatus transactionStatus;
    private final long              blockIndex;

    public TransactionStatusResult(@NonNull TransactionStatus transactionStatus,
                                   long blockIndex)
    {
      this.transactionStatus = transactionStatus;
      this.blockIndex        = blockIndex;
    }

    static TransactionStatusResult inProgress() {
      return new TransactionStatusResult(TransactionStatus.IN_PROGRESS, 0);
    }

    static TransactionStatusResult failed() {
      return new TransactionStatusResult(TransactionStatus.FAILED, 0);
    }

    static TransactionStatusResult complete(long blockIndex) {
      return new TransactionStatusResult(TransactionStatus.COMPLETE, blockIndex);
    }

    public @NonNull TransactionStatus getTransactionStatus() {
      return transactionStatus;
    }

    public long getBlockIndex() {
      return blockIndex;
    }
  }

  public static final class ReceivedTransactionStatus {

    private final TransactionStatus status;
    private final Money             amount;
    private final long              blockIndex;

    public static ReceivedTransactionStatus failed() {
      return new ReceivedTransactionStatus(TransactionStatus.FAILED, null, 0);
    }

    public static ReceivedTransactionStatus inProgress() {
      return new ReceivedTransactionStatus(TransactionStatus.IN_PROGRESS, null, 0);
    }

    public static ReceivedTransactionStatus complete(@NonNull Money amount, long blockIndex) {
      return new ReceivedTransactionStatus(TransactionStatus.COMPLETE, amount, blockIndex);
    }

    private ReceivedTransactionStatus(@NonNull TransactionStatus status, @Nullable Money amount, long blockIndex) {
      this.status     = status;
      this.amount     = amount;
      this.blockIndex = blockIndex;
    }

    public @NonNull TransactionStatus getStatus() {
      return status;
    }

    public @NonNull Money getAmount() {
      if (status != TransactionStatus.COMPLETE || amount == null) {
        throw new IllegalStateException();
      }
      return amount;
    }

    public long getBlockIndex() {
      return blockIndex;
    }
  }

  private static class DefragDelegate implements DefragmentationDelegate {
    private final MobileCoinClient                  mobileCoinClient;
    private final List<TransactionSubmissionResult> results;
    private       Money.MobileCoin                  totalFeesSpent = Money.MobileCoin.ZERO;

    DefragDelegate(@NonNull MobileCoinClient mobileCoinClient, @NonNull List<TransactionSubmissionResult> results) {
      this.mobileCoinClient = mobileCoinClient;
      this.results          = results;
    }

    @Override
    public void onStart() {
      Log.i(TAG, "Defragmenting start");
    }

    @Override
    public boolean onStepReady(@NonNull PendingTransaction pendingTransaction, @NonNull BigInteger fee)
        throws NetworkException, InvalidTransactionException, AttestationException
    {
      Log.i(TAG, "Submitting defrag transaction");
      mobileCoinClient.submitTransaction(pendingTransaction.getTransaction());
      Log.i(TAG, "Defrag transaction submitted");
      try {
        Money.MobileCoin defragFee = Money.picoMobileCoin(fee);
        results.add(TransactionSubmissionResult.successfullySubmittedDefrag(new PaymentTransactionId.MobileCoin(pendingTransaction.getTransaction().toByteArray(), pendingTransaction.getReceipt().toByteArray(), defragFee)));
        totalFeesSpent = totalFeesSpent.add(defragFee).requireMobileCoin();
      } catch (SerializationException e) {
        throw new AssertionError(e);
      }
      return true;
    }

    @Override
    public void onComplete() {
      Log.i(TAG, "Defragmenting complete");
    }

    @Override
    public void onCancel() {
      Log.w(TAG, "Defragmenting cancel");
    }
  }
}
