package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.mobilecoin.lib.KeyImage;
import com.mobilecoin.lib.Receipt;
import com.mobilecoin.lib.RistrettoPublic;
import com.mobilecoin.lib.Transaction;
import com.mobilecoin.lib.exceptions.SerializationException;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import okio.ByteString;

public final class PaymentMetaDataUtil {

  public static PaymentMetaData parseOrThrow(byte[] requireBlob) {
    try {
      return PaymentMetaData.ADAPTER.decode(requireBlob);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static @NonNull PaymentMetaData fromReceipt(@Nullable byte[] receipt) throws SerializationException {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = new PaymentMetaData.MobileCoinTxoIdentification.Builder();

    if (receipt != null) {
      addReceiptData(receipt, builder);
    }

    return new PaymentMetaData.Builder().mobileCoinTxoIdentification(builder.build()).build();
  }

  public static @NonNull PaymentMetaData fromKeysAndImages(@NonNull List<ByteString> publicKeys, @NonNull List<ByteString> keyImages) {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = new PaymentMetaData.MobileCoinTxoIdentification.Builder();

    builder.keyImages(keyImages);
    builder.publicKey(publicKeys);

    return new PaymentMetaData.Builder().mobileCoinTxoIdentification(builder.build()).build();
  }

  public static @NonNull PaymentMetaData fromReceiptAndTransaction(@Nullable byte[] receipt, @Nullable byte[] transaction) throws SerializationException {
    PaymentMetaData.MobileCoinTxoIdentification.Builder builder = new PaymentMetaData.MobileCoinTxoIdentification.Builder();

    if (transaction != null) {
      addTransactionData(transaction, builder);
    } else if (receipt != null) {
      addReceiptData(receipt, builder);
    }

    return new PaymentMetaData.Builder().mobileCoinTxoIdentification(builder.build()).build();
  }

  private static void addReceiptData(@NonNull byte[] receipt, PaymentMetaData.MobileCoinTxoIdentification.Builder builder) throws SerializationException {
    RistrettoPublic publicKey = Receipt.fromBytes(receipt).getPublicKey();
    addPublicKey(builder, publicKey);
  }

  private static void addTransactionData(@NonNull byte[] transactionBytes, PaymentMetaData.MobileCoinTxoIdentification.Builder builder) throws SerializationException {
    Transaction   transaction = Transaction.fromBytes(transactionBytes);
    Set<KeyImage> keyImages   = transaction.getKeyImages();

    List<ByteString> newKeyImages = new ArrayList<>(builder.keyImages);
    for (KeyImage keyImage : keyImages) {
      newKeyImages.add(ByteString.of(keyImage.getData()));
    }
    builder.keyImages(newKeyImages);

    for (RistrettoPublic publicKey : transaction.getOutputPublicKeys()) {
      addPublicKey(builder, publicKey);
    }
  }

  private static void addPublicKey(@NonNull PaymentMetaData.MobileCoinTxoIdentification.Builder builder, @NonNull RistrettoPublic publicKey) {
    List<ByteString> publicKeys = new ArrayList<>(builder.publicKey);
    publicKeys.add(ByteString.of(publicKey.getKeyBytes()));
    builder.publicKey(publicKeys);
  }

  public static byte[] receiptPublic(@NonNull PaymentMetaData paymentMetaData) {
    return Stream.of(paymentMetaData.mobileCoinTxoIdentification.publicKey).single().toByteArray();
  }
}
