package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mobilecoin.lib.KeyImage;
import com.mobilecoin.lib.Receipt;
import com.mobilecoin.lib.RistrettoPublic;
import com.mobilecoin.lib.Transaction;
import com.mobilecoin.lib.exceptions.SerializationException;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;

import java.util.List;
import java.util.Set;

public final class PaymentMetaDataUtil {

  public static PaymentMetaData parseOrThrow(byte[] requireBlob) {
    try {
      return PaymentMetaData.parseFrom(requireBlob);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
  }

  public static @NonNull PaymentMetaData fromReceipt(@Nullable byte[] receipt) throws SerializationException {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = PaymentMetaData.MobileCoinTxoIdentification.newBuilder();

    if (receipt != null) {
      addReceiptData(receipt, builder);
    }

    return PaymentMetaData.newBuilder().setMobileCoinTxoIdentification(builder).build();
  }

  public static @NonNull PaymentMetaData fromKeysAndImages(@NonNull List<ByteString> publicKeys, @NonNull List<ByteString> keyImages) {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = PaymentMetaData.MobileCoinTxoIdentification.newBuilder();

    builder.addAllKeyImages(keyImages);
    builder.addAllPublicKey(publicKeys);

    return PaymentMetaData.newBuilder().setMobileCoinTxoIdentification(builder).build();
  }

  public static @NonNull PaymentMetaData fromReceiptAndTransaction(@Nullable byte[] receipt, @Nullable byte[] transaction) throws SerializationException {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = PaymentMetaData.MobileCoinTxoIdentification.newBuilder();

    if (transaction != null) {
      addTransactionData(transaction, builder);
    } else if (receipt != null) {
      addReceiptData(receipt, builder);
    }

    return PaymentMetaData.newBuilder().setMobileCoinTxoIdentification(builder).build();
  }

  private static void addReceiptData(@NonNull byte[] receipt, PaymentMetaData.MobileCoinTxoIdentification.Builder builder) throws SerializationException {
    RistrettoPublic publicKey = Receipt.fromBytes(receipt).getPublicKey();
    addPublicKey(builder, publicKey);
  }

  private static void addTransactionData(@NonNull byte[] transactionBytes, PaymentMetaData.MobileCoinTxoIdentification.Builder builder) throws SerializationException {
    Transaction   transaction = Transaction.fromBytes(transactionBytes);
    Set<KeyImage> keyImages   = transaction.getKeyImages();
    for (KeyImage keyImage : keyImages) {
      builder.addKeyImages(ByteString.copyFrom(keyImage.getData()));
    }
    for (RistrettoPublic publicKey : transaction.getOutputPublicKeys()) {
      addPublicKey(builder, publicKey);
    }
  }

  private static void addPublicKey(@NonNull PaymentMetaData.MobileCoinTxoIdentification.Builder builder, @NonNull RistrettoPublic publicKey) {
    builder.addPublicKey(ByteString.copyFrom(publicKey.getKeyBytes()));
  }

  public static byte[] receiptPublic(@NonNull PaymentMetaData paymentMetaData) {
    return Stream.of(paymentMetaData.getMobileCoinTxoIdentification().getPublicKeyList()).single().toByteArray();
  }
}
