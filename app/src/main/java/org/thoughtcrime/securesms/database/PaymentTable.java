package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mobilecoin.lib.exceptions.SerializationException;

import org.signal.core.util.CursorExtensionsKt;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.CryptoValue;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.payments.CryptoValueUtil;
import org.thoughtcrime.securesms.payments.Direction;
import org.thoughtcrime.securesms.payments.FailureReason;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.State;
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class PaymentTable extends DatabaseTable implements RecipientIdDatabaseReference {

  private static final String TAG = Log.tag(PaymentTable.class);

  public static final String TABLE_NAME = "payments";

  private static final String ID           = "_id";
  private static final String PAYMENT_UUID = "uuid";
  private static final String RECIPIENT_ID = "recipient";
  private static final String ADDRESS      = "recipient_address";
  private static final String TIMESTAMP    = "timestamp";
  private static final String DIRECTION    = "direction";
  private static final String STATE        = "state";
  private static final String NOTE         = "note";
  private static final String AMOUNT       = "amount";
  private static final String FEE          = "fee";
  private static final String TRANSACTION  = "transaction_record";
  private static final String RECEIPT      = "receipt";
  private static final String PUBLIC_KEY   = "receipt_public_key";
  private static final String META_DATA    = "payment_metadata";
  private static final String FAILURE      = "failure_reason";
  private static final String BLOCK_INDEX  = "block_index";
  private static final String BLOCK_TIME   = "block_timestamp";
  private static final String SEEN         = "seen";

  public static final String CREATE_TABLE =
    "CREATE TABLE " + TABLE_NAME + "(" + ID           + " INTEGER PRIMARY KEY, " +
                                         PAYMENT_UUID + " TEXT DEFAULT NULL, " +
                                         RECIPIENT_ID + " INTEGER DEFAULT 0, " +
                                         ADDRESS      + " TEXT DEFAULT NULL, " +
                                         TIMESTAMP    + " INTEGER, " +
                                         NOTE         + " TEXT DEFAULT NULL, " +
                                         DIRECTION    + " INTEGER, " +
                                         STATE        + " INTEGER, " +
                                         FAILURE      + " INTEGER, " +
                                         AMOUNT       + " BLOB NOT NULL, " +
                                         FEE          + " BLOB NOT NULL, " +
                                         TRANSACTION  + " BLOB DEFAULT NULL, " +
                                         RECEIPT      + " BLOB DEFAULT NULL, " +
                                         META_DATA    + " BLOB DEFAULT NULL, " +
                                         PUBLIC_KEY   + " TEXT DEFAULT NULL, " +
                                         BLOCK_INDEX  + " INTEGER DEFAULT 0, " +
                                         BLOCK_TIME   + " INTEGER DEFAULT 0, " +
                                         SEEN         + " INTEGER, " +
                                         "UNIQUE(" + PAYMENT_UUID + ") ON CONFLICT ABORT)";

  public static final String[] CREATE_INDEXES = {
    "CREATE INDEX IF NOT EXISTS timestamp_direction_index ON " + TABLE_NAME + " (" + TIMESTAMP + ", " + DIRECTION + ");",
    "CREATE INDEX IF NOT EXISTS timestamp_index ON " + TABLE_NAME + " (" + TIMESTAMP + ");",
    "CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON " + TABLE_NAME + " (" + PUBLIC_KEY + ");"
  };

  private final MutableLiveData<Object> changeSignal;

  PaymentTable(@NonNull Context context, @NonNull SignalDatabase databaseHelper) {
    super(context, databaseHelper);

    this.changeSignal = new MutableLiveData<>(new Object());
  }

  @WorkerThread
  public void createIncomingPayment(@NonNull UUID uuid,
                                    @Nullable RecipientId fromRecipient,
                                    long timestamp,
                                    @NonNull String note,
                                    @NonNull Money amount,
                                    @NonNull Money fee,
                                    @NonNull byte[] receipt,
                                    boolean seen)
      throws PublicKeyConflictException, SerializationException
  {
    create(uuid, fromRecipient, null, timestamp, 0, note, Direction.RECEIVED, State.SUBMITTED, amount, fee, null, receipt, null, seen);
  }

  @WorkerThread
  public void createOutgoingPayment(@NonNull UUID uuid,
                                    @Nullable RecipientId toRecipient,
                                    @NonNull MobileCoinPublicAddress publicAddress,
                                    long timestamp,
                                    @NonNull String note,
                                    @NonNull Money amount)
  {
    try {
      create(uuid, toRecipient, publicAddress, timestamp, 0, note, Direction.SENT, State.INITIAL, amount, amount.toZero(), null, null, null, true);
    } catch (PublicKeyConflictException e) {
      Log.w(TAG, "Tried to create payment but the public key appears already in the database", e);
      throw new IllegalArgumentException(e);
    } catch (SerializationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Inserts a payment in its final successful state.
   * <p>
   * This is for when a linked device has told us about the payment only.
   */
  @WorkerThread
  public void createSuccessfulPayment(@NonNull UUID uuid,
                                      @Nullable RecipientId toRecipient,
                                      @NonNull MobileCoinPublicAddress publicAddress,
                                      long timestamp,
                                      long blockIndex,
                                      @NonNull String note,
                                      @NonNull Money amount,
                                      @NonNull Money fee,
                                      @NonNull byte[] receipt,
                                      @NonNull PaymentMetaData metaData)
    throws SerializationException
  {
    try {
      create(uuid, toRecipient, publicAddress, timestamp, blockIndex, note, Direction.SENT, State.SUCCESSFUL, amount, fee, null, receipt, metaData, true);
    } catch (PublicKeyConflictException e) {
      Log.w(TAG, "Tried to create payment but the public key appears already in the database", e);
      throw new AssertionError(e);
    }
  }

  @WorkerThread
  public void createDefrag(@NonNull UUID uuid,
                           @Nullable RecipientId self,
                           @NonNull MobileCoinPublicAddress selfPublicAddress,
                           long timestamp,
                           @NonNull Money fee,
                           @NonNull byte[] transaction,
                           @NonNull byte[] receipt)
  {
    try {
      create(uuid, self, selfPublicAddress, timestamp, 0, "", Direction.SENT, State.SUBMITTED, fee.toZero(), fee, transaction, receipt, null, true);
    } catch (PublicKeyConflictException e) {
      Log.w(TAG, "Tried to create payment but the public key appears already in the database", e);
      throw new AssertionError(e);
    } catch (SerializationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @WorkerThread
  public UUID restoreFromBackup(@NonNull RecipientId recipientId,
                                long timestamp,
                                long blockIndex,
                                long blockTimestamp,
                                @NonNull String note,
                                @NonNull Direction direction,
                                @NonNull State state,
                                @NonNull Money amount,
                                @NonNull Money fee,
                                @Nullable byte[] transaction,
                                @Nullable byte[] receipt,
                                @Nullable PaymentMetaData metaData,
                                boolean seen,
                                @Nullable FailureReason failureReason)
  {
    UUID uuid = UUID.randomUUID();
    try {
      create(uuid, recipientId, null, timestamp, blockIndex, note, direction, state, amount, fee, transaction, receipt, metaData, seen);
      updateBlockDetails(uuid, blockIndex, blockTimestamp);
      if (failureReason != null) {
        markPaymentFailed(uuid, failureReason);
      }
    } catch (SerializationException | PublicKeyConflictException e) {
      return null;
    }
    return uuid;
  }

  @WorkerThread
  private void create(@NonNull UUID uuid,
                      @Nullable RecipientId recipientId,
                      @Nullable MobileCoinPublicAddress publicAddress,
                      long timestamp,
                      long blockIndex,
                      @NonNull String note,
                      @NonNull Direction direction,
                      @NonNull State state,
                      @NonNull Money amount,
                      @NonNull Money fee,
                      @Nullable byte[] transaction,
                      @Nullable byte[] receipt,
                      @Nullable PaymentMetaData metaData,
                      boolean seen)
      throws PublicKeyConflictException, SerializationException
  {
    if (recipientId == null && publicAddress == null) {
      throw new AssertionError();
    }

    if (amount.isNegative()) {
      throw new AssertionError();
    }

    if (fee.isNegative()) {
      throw new AssertionError();
    }

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    ContentValues  values   = new ContentValues(15);

    values.put(PAYMENT_UUID, uuid.toString());
    if (recipientId == null || recipientId.isUnknown()) {
      values.put(RECIPIENT_ID, 0);
    } else {
      values.put(RECIPIENT_ID, recipientId.serialize());
    }
    if (publicAddress == null) {
      values.putNull(ADDRESS);
    } else {
      values.put(ADDRESS, publicAddress.getPaymentAddressBase58());
    }
    values.put(TIMESTAMP, timestamp);
    values.put(BLOCK_INDEX, blockIndex);
    values.put(NOTE, note);
    values.put(DIRECTION, direction.serialize());
    values.put(STATE, state.serialize());
    values.put(AMOUNT, CryptoValueUtil.moneyToCryptoValue(amount).encode());
    values.put(FEE, CryptoValueUtil.moneyToCryptoValue(fee).encode());
    if (transaction != null) {
      values.put(TRANSACTION, transaction);
    } else {
      values.putNull(TRANSACTION);
    }
    if (receipt != null) {
      values.put(RECEIPT, receipt);
      values.put(PUBLIC_KEY, Base64.encodeWithPadding(PaymentMetaDataUtil.receiptPublic(PaymentMetaDataUtil.fromReceipt(receipt))));
    } else {
      values.putNull(RECEIPT);
      values.putNull(PUBLIC_KEY);
    }
    if (metaData != null) {
      values.put(META_DATA, metaData.encode());
    } else {
      values.put(META_DATA, PaymentMetaDataUtil.fromReceiptAndTransaction(receipt, transaction).encode());
    }
    values.put(SEEN, seen ? 1 : 0);

    long inserted = database.insert(TABLE_NAME, null, values);

    if (inserted == -1) {
      throw new PublicKeyConflictException();
    }

    notifyChanged(uuid);
  }

  public void deleteAll() {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, null, null);
    Log.i(TAG, "Deleted all records");
  }

  @WorkerThread
  public boolean delete(@NonNull UUID uuid) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    String         where    = PAYMENT_UUID + " = ?";
    String[]       args     = {uuid.toString()};
    int            deleted;

    database.beginTransaction();
    try {
      deleted = database.delete(TABLE_NAME, where, args);

      if (deleted > 1) {
        Log.w(TAG, "More than one row matches criteria");
        throw new AssertionError();
      }
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    if (deleted > 0) {
      notifyChanged(uuid);
    }

    return deleted > 0;
  }

  @WorkerThread
  public @NonNull List<PaymentTransaction> getAll() {
    SQLiteDatabase           database = databaseHelper.getSignalReadableDatabase();
    List<PaymentTransaction> result   = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, TIMESTAMP + " DESC")) {
      while (cursor.moveToNext()) {
        result.add(readPayment(cursor));
      }
    }

    return result;
  }

  @WorkerThread
  public void markAllSeen() {
    SQLiteDatabase database         = databaseHelper.getSignalWritableDatabase();
    ContentValues  values           = new ContentValues(1);
    List<UUID>     unseenIds        = new LinkedList<>();
    String[]       unseenProjection = SqlUtil.buildArgs(PAYMENT_UUID);
    String         unseenWhile      = SEEN + " != ?";
    String[]       unseenArgs       = SqlUtil.buildArgs("1");
    int            updated          = -1;

    values.put(SEEN, 1);

    try {
      database.beginTransaction();

      try (Cursor cursor = database.query(TABLE_NAME, unseenProjection, unseenWhile, unseenArgs, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          unseenIds.add(UUID.fromString(CursorUtil.requireString(cursor, PAYMENT_UUID)));
        }
      }

      if (!unseenIds.isEmpty()) {
        updated = database.update(TABLE_NAME, values, null, null);
      }

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    if (updated > 0) {
      for (final UUID unseenId : unseenIds) {
        notifyUuidChanged(unseenId);
      }

      notifyChanged();
    }
  }

  @WorkerThread
  public void markPaymentSeen(@NonNull UUID uuid) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    ContentValues  values   = new ContentValues(1);
    String         where    = PAYMENT_UUID + " = ?";
    String[]       args     = {uuid.toString()};

    values.put(SEEN, 1);
    int updated = database.update(TABLE_NAME, values, where, args);

    if (updated > 0) {
      notifyChanged(uuid);
    }
  }

  @WorkerThread
  public @NonNull List<PaymentTransaction> getUnseenPayments() {
    SQLiteDatabase           db      = databaseHelper.getSignalReadableDatabase();
    String                   query   = SEEN + " = 0 AND " + STATE + " = " + State.SUCCESSFUL.serialize();
    List<PaymentTransaction> results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, null, query, null, null, null, null)) {
      while (cursor.moveToNext()) {
        results.add(readPayment(cursor));
      }
    }

    return results;
  }

  @WorkerThread
  public @Nullable PaymentTransaction getPayment(@NonNull UUID uuid) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         select   = PAYMENT_UUID + " = ?";
    String[]       args     = {uuid.toString()};

    try (Cursor cursor = database.query(TABLE_NAME, null, select, args, null, null, null)) {
      if (cursor.moveToNext()) {
        PaymentTransaction payment = readPayment(cursor);

        if (cursor.moveToNext()) {
          throw new AssertionError("Multiple records for one UUID");
        }

        return payment;
      } else {
        return null;
      }
    }
  }

  public @NonNull List<Payment> getPayments(@Nullable Collection<UUID> paymentUuids) {
    if (paymentUuids == null || paymentUuids.isEmpty()) {
      return Collections.emptyList();
    }

    List<SqlUtil.Query> queries  = SqlUtil.buildCollectionQuery(PAYMENT_UUID, paymentUuids);
    List<Payment>       payments = new LinkedList<>();

    for (SqlUtil.Query query : queries) {
      Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase())
                                                .from(TABLE_NAME)
                                                .where(query.getWhere(), (Object[]) query.getWhereArgs())
                                                .run();

      payments.addAll(CursorExtensionsKt.readToList(cursor, PaymentTable::readPayment));
    }

    return payments;
  }

  public @NonNull List<UUID> getSubmittedIncomingPayments() {
    return CursorExtensionsKt.readToList(
        SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), PAYMENT_UUID)
                                  .from(TABLE_NAME)
                                  .where(DIRECTION + " = ? AND " + STATE + " = ?", Direction.RECEIVED.serialize(), State.SUBMITTED.serialize())
                                  .run(),
        c -> UuidUtil.parseOrNull(CursorUtil.requireString(c, PAYMENT_UUID))
    );
  }

  @AnyThread
  public @NonNull LiveData<List<PaymentTransaction>> getAllLive() {
    return LiveDataUtil.mapAsync(changeSignal, change -> getAll());
  }

  @WorkerThread
  public @NonNull MessageRecord updateMessageWithPayment(@NonNull MessageRecord record) {
    if (record.isPaymentNotification()) {
      Payment payment = getPayment(UuidUtil.parseOrThrow(record.getBody()));
      if (payment != null && record instanceof MmsMessageRecord) {
        return ((MmsMessageRecord) record).withPayment(payment);
      } else {
        Log.w(TAG, "Payment not found for message");
      }
    }
    return record;
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, toId.serialize());
    getWritableDatabase().update(TABLE_NAME, values, RECIPIENT_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  public boolean markPaymentSubmitted(@NonNull UUID uuid,
                                      @NonNull byte[] transaction,
                                      @NonNull byte[] receipt,
                                      @NonNull Money fee)
      throws PublicKeyConflictException
  {
    SQLiteDatabase database  = databaseHelper.getSignalWritableDatabase();
    String         where     = PAYMENT_UUID + " = ?";
    String[]       whereArgs = {uuid.toString()};
    int            updated;
    ContentValues  values    = new ContentValues(6);

    values.put(STATE, State.SUBMITTED.serialize());
    values.put(TRANSACTION, transaction);
    values.put(RECEIPT, receipt);
    try {
      values.put(PUBLIC_KEY, Base64.encodeWithPadding(PaymentMetaDataUtil.receiptPublic(PaymentMetaDataUtil.fromReceipt(receipt))));
      values.put(META_DATA, PaymentMetaDataUtil.fromReceiptAndTransaction(receipt, transaction).encode());
    } catch (SerializationException e) {
      throw new IllegalArgumentException(e);
    }
    values.put(FEE, CryptoValueUtil.moneyToCryptoValue(fee).encode());

    database.beginTransaction();
    try {
      updated = database.update(TABLE_NAME, values, where, whereArgs);

      if (updated == -1) {
        throw new PublicKeyConflictException();
      }

      if (updated > 1) {
        Log.w(TAG, "More than one row matches criteria");
        throw new AssertionError();
      }
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    if (updated > 0) {
      notifyChanged(uuid);
    }

    return updated > 0;
  }

  public boolean markPaymentSuccessful(@NonNull UUID uuid, long blockIndex) {
    return markPayment(uuid, State.SUCCESSFUL, null, null, blockIndex);
  }

  public boolean markReceivedPaymentSuccessful(@NonNull UUID uuid, @NonNull Money amount, long blockIndex) {
    return markPayment(uuid, State.SUCCESSFUL, amount, null, blockIndex);
  }

  public boolean markPaymentFailed(@NonNull UUID uuid, @NonNull FailureReason failureReason) {
    return markPayment(uuid, State.FAILED, null, failureReason, null);
  }

  private boolean markPayment(@NonNull UUID uuid,
                              @NonNull State state,
                              @Nullable Money amount,
                              @Nullable FailureReason failureReason,
                              @Nullable Long blockIndex)
  {
    SQLiteDatabase database  = databaseHelper.getSignalWritableDatabase();
    String         where     = PAYMENT_UUID + " = ?";
    String[]       whereArgs = {uuid.toString()};
    int            updated;
    ContentValues  values    = new ContentValues(3);

    values.put(STATE, state.serialize());

    if (amount != null) {
      values.put(AMOUNT, CryptoValueUtil.moneyToCryptoValue(amount).encode());
    }

    if (state == State.FAILED) {
      values.put(FAILURE, failureReason != null ? failureReason.serialize()
                                                : FailureReason.UNKNOWN.serialize());
    } else {
      if (failureReason != null) {
        throw new AssertionError();
      }
      values.putNull(FAILURE);
    }

    if (blockIndex != null) {
      values.put(BLOCK_INDEX, blockIndex);
    }

    database.beginTransaction();
    try {
      updated = database.update(TABLE_NAME, values, where, whereArgs);

      if (updated > 1) {
        Log.w(TAG, "More than one row matches criteria");
        throw new AssertionError();
      }
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    if (updated > 0) {
      notifyChanged(uuid);
    }

    return updated > 0;
  }

  public boolean updateBlockDetails(@NonNull UUID uuid,
                                    long blockIndex,
                                    long blockTimestamp)
  {
    SQLiteDatabase database  = databaseHelper.getSignalWritableDatabase();
    String         where     = PAYMENT_UUID + " = ?";
    String[]       whereArgs = {uuid.toString()};
    int            updated;
    ContentValues  values    = new ContentValues(2);

    values.put(BLOCK_INDEX, blockIndex);
    values.put(BLOCK_TIME, blockTimestamp);

    database.beginTransaction();
    try {
      updated = database.update(TABLE_NAME, values, where, whereArgs);

      if (updated > 1) {
        Log.w(TAG, "More than one row matches criteria");
        throw new AssertionError();
      }
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    if (updated > 0) {
      notifyChanged(uuid);
    }

    return updated > 0;
  }

  private static @NonNull PaymentTransaction readPayment(@NonNull Cursor cursor) {
    State         state         = State.deserialize(CursorUtil.requireInt(cursor, STATE));
    FailureReason failureReason = null;

    if (state == State.FAILED) {
      failureReason = FailureReason.deserialize(CursorUtil.requireInt(cursor, FAILURE));
    }

    return new PaymentTransaction(UUID.fromString(CursorUtil.requireString(cursor, PAYMENT_UUID)),
                                  getRecipientId(cursor),
                                  MobileCoinPublicAddress.fromBase58NullableOrThrow(CursorUtil.requireString(cursor, ADDRESS)),
                                  CursorUtil.requireLong(cursor, TIMESTAMP),
                                  Direction.deserialize(CursorUtil.requireInt(cursor, DIRECTION)),
                                  state,
                                  failureReason,
                                  CursorUtil.requireString(cursor, NOTE),
                                  getMoneyValue(CursorUtil.requireBlob(cursor, AMOUNT)),
                                  getMoneyValue(CursorUtil.requireBlob(cursor, FEE)),
                                  CursorUtil.requireBlob(cursor, TRANSACTION),
                                  CursorUtil.requireBlob(cursor, RECEIPT),
                                  PaymentMetaDataUtil.parseOrThrow(CursorUtil.requireBlob(cursor, META_DATA)),
                                  CursorUtil.requireLong(cursor, BLOCK_INDEX),
                                  CursorUtil.requireLong(cursor, BLOCK_TIME),
                                  CursorUtil.requireBoolean(cursor, SEEN));
  }

  private static @Nullable RecipientId getRecipientId(@NonNull Cursor cursor) {
    long id = CursorUtil.requireLong(cursor, RECIPIENT_ID);
    if (id == 0) return null;
    return RecipientId.from(id);
  }

  private static @NonNull Money getMoneyValue(@NonNull byte[] blob) {
    try {
      CryptoValue cryptoValue = CryptoValue.ADAPTER.decode(blob);
      return CryptoValueUtil.cryptoValueToMoney(cryptoValue);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * notifyChanged will alert the database observer for two events:
   *
   * 1. It will alert the global payments observer that something changed
   * 2. It will alert the uuid specific observer that something will change.
   *
   * You should not call this in a tight loop, opting to call notifyUuidChanged instead.
   */
  private void notifyChanged(@Nullable UUID uuid) {
    notifyChanged();
    notifyUuidChanged(uuid);
  }

  /**
   * Notifies the global payments observer that something changed.
   */
  private void notifyChanged() {
    changeSignal.postValue(new Object());
    AppDependencies.getDatabaseObserver().notifyAllPaymentsListeners();
  }

  /**
   * Notify the database observer of a change for a specific uuid. Does not trigger
   * the global payments observer.
   */
  private void notifyUuidChanged(@Nullable UUID uuid) {
    if (uuid != null) {
      AppDependencies.getDatabaseObserver().notifyPaymentListeners(uuid);
      MessageId messageId = SignalDatabase.messages().getPaymentMessage(uuid);
      if (messageId != null) {
        AppDependencies.getDatabaseObserver().notifyMessageUpdateObservers(messageId);
      }
    }
  }

  public static final class PaymentTransaction implements Payment {
    private final UUID            uuid;
    private final Payee           payee;
    private final long            timestamp;
    private final Direction       direction;
    private final State           state;
    private final FailureReason   failureReason;
    private final String          note;
    private final Money           amount;
    private final Money           fee;
    private final byte[]          transaction;
    private final byte[]          receipt;
    private final PaymentMetaData paymentMetaData;
    private final Long            blockIndex;
    private final long            blockTimestamp;
    private final boolean         seen;

    PaymentTransaction(@NonNull UUID uuid,
                       @Nullable RecipientId recipientId,
                       @Nullable MobileCoinPublicAddress publicAddress,
                       long timestamp,
                       @NonNull Direction direction,
                       @NonNull State state,
                       @Nullable FailureReason failureReason,
                       @NonNull String note,
                       @NonNull Money amount,
                       @NonNull Money fee,
                       @Nullable byte[] transaction,
                       @Nullable byte[] receipt,
                       @NonNull PaymentMetaData paymentMetaData,
                       @Nullable Long blockIndex,
                       long blockTimestamp,
                       boolean seen)
    {
      this.uuid            = uuid;
      this.paymentMetaData = paymentMetaData;
      this.payee           = fromPaymentTransaction(recipientId, publicAddress);
      this.timestamp       = timestamp;
      this.direction       = direction;
      this.state           = state;
      this.failureReason   = failureReason;
      this.note            = note;
      this.amount          = amount;
      this.fee             = fee;
      this.transaction     = transaction;
      this.receipt         = receipt;
      this.blockIndex      = blockIndex;
      this.blockTimestamp  = blockTimestamp;
      this.seen            = seen;

      if (amount.isNegative()) {
        throw new AssertionError();
      }
    }

    @Override
    public @NonNull UUID getUuid() {
      return uuid;
    }

    @Override
    public @NonNull Payee getPayee() {
      return payee;
    }

    @Override
    public long getBlockIndex() {
      return blockIndex;
    }

    @Override
    public long getBlockTimestamp() {
      return blockTimestamp;
    }

    @Override
    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public @NonNull Direction getDirection() {
      return direction;
    }

    @Override
    public @NonNull State getState() {
      return state;
    }

    @Override
    public @Nullable FailureReason getFailureReason() {
      return failureReason;
    }

    @Override
    public @NonNull String getNote() {
      return note;
    }

    @Override
    public @NonNull Money getAmount() {
      return amount;
    }

    @Override
    public @NonNull Money getFee() {
      return fee;
    }

    @Override
    public @NonNull PaymentMetaData getPaymentMetaData() {
      return paymentMetaData;
    }

    @Override
    public boolean isSeen() {
      return seen;
    }

    public @Nullable byte[] getTransaction() {
      return transaction;
    }

    public @Nullable byte[] getReceipt() {
      return receipt;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) return true;
      if (!(o instanceof PaymentTransaction)) return false;

      final PaymentTransaction other = (PaymentTransaction) o;

      return timestamp == other.timestamp &&
             uuid.equals(other.uuid) &&
             payee.equals(other.payee) &&
             direction == other.direction &&
             state == other.state &&
             note.equals(other.note) &&
             amount.equals(other.amount) &&
             Arrays.equals(transaction, other.transaction) &&
             Arrays.equals(receipt, other.receipt) &&
             paymentMetaData.equals(other.paymentMetaData);
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + payee.hashCode();
      result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
      result = 31 * result + direction.hashCode();
      result = 31 * result + state.hashCode();
      result = 31 * result + note.hashCode();
      result = 31 * result + amount.hashCode();
      result = 31 * result + Arrays.hashCode(transaction);
      result = 31 * result + Arrays.hashCode(receipt);
      result = 31 * result + paymentMetaData.hashCode();
      return result;
    }
  }

  private static @NonNull Payee fromPaymentTransaction(@Nullable RecipientId recipientId, @Nullable MobileCoinPublicAddress publicAddress) {
    if (recipientId == null && publicAddress == null) {
      throw new AssertionError();
    }

    if (recipientId != null) {
      return Payee.fromRecipientAndAddress(recipientId, publicAddress);
    } else {
      return new Payee(publicAddress);
    }
  }

  public final class PublicKeyConflictException extends Exception {
    private PublicKeyConflictException() {}
  }
}
