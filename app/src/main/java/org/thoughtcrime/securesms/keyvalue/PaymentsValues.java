package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mobilecoin.lib.Mnemonics;
import com.mobilecoin.lib.exceptions.BadMnemonicException;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.payments.Entropy;
import org.thoughtcrime.securesms.payments.GeographicalRestrictions;
import org.thoughtcrime.securesms.payments.Mnemonic;
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper;
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil;
import org.thoughtcrime.securesms.payments.proto.MobileCoinLedger;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public final class PaymentsValues extends SignalStoreValues {

  private static final String TAG = Log.tag(PaymentsValues.class);

  private static final String PAYMENTS_ENTROPY          = "payments_entropy";
  private static final String MOB_PAYMENTS_ENABLED      = "mob_payments_enabled";
  private static final String MOB_LEDGER                = "mob_ledger";
  private static final String PAYMENTS_CURRENT_CURRENCY = "payments_current_currency";
  private static final String DEFAULT_CURRENCY_CODE     = "GBP";
  private static final String USER_CONFIRMED_MNEMONIC   = "mob_payments_user_confirmed_mnemonic";

  private static final String SHOW_ABOUT_MOBILE_COIN_INFO_CARD     = "mob_payments_show_about_mobile_coin_info_card";
  private static final String SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD = "mob_payments_show_adding_to_your_wallet_info_card";
  private static final String SHOW_CASHING_OUT_INFO_CARD           = "mob_payments_show_cashing_out_info_card";
  private static final String SHOW_RECOVERY_PHRASE_INFO_CARD       = "mob_payments_show_recovery_phrase_info_card";
  private static final String SHOW_UPDATE_PIN_INFO_CARD            = "mob_payments_show_update_pin_info_card";

  private static final Money.MobileCoin LARGE_BALANCE_THRESHOLD = Money.mobileCoin(BigDecimal.valueOf(500));

  private final MutableLiveData<Currency>                liveCurrentCurrency;
  private final MutableLiveData<MobileCoinLedgerWrapper> liveMobileCoinLedger;
  private final LiveData<Balance>                        liveMobileCoinBalance;

  PaymentsValues(@NonNull KeyValueStore store) {
    super(store);
    this.liveCurrentCurrency   = new MutableLiveData<>(currentCurrency());
    this.liveMobileCoinLedger  = new MutableLiveData<>(mobileCoinLatestFullLedger());
    this.liveMobileCoinBalance = Transformations.map(liveMobileCoinLedger, MobileCoinLedgerWrapper::getBalance);
  }

  @Override void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(PAYMENTS_ENTROPY,
                         MOB_PAYMENTS_ENABLED,
                         MOB_LEDGER,
                         PAYMENTS_CURRENT_CURRENCY,
                         DEFAULT_CURRENCY_CODE,
                         USER_CONFIRMED_MNEMONIC,
                         SHOW_ABOUT_MOBILE_COIN_INFO_CARD,
                         SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD,
                         SHOW_CASHING_OUT_INFO_CARD,
                         SHOW_RECOVERY_PHRASE_INFO_CARD,
                         SHOW_UPDATE_PIN_INFO_CARD);
  }

  public boolean userConfirmedMnemonic() {
    return getStore().getBoolean(USER_CONFIRMED_MNEMONIC, false);
  }

  public void setUserConfirmedMnemonic(boolean userConfirmedMnemonic) {
    getStore().beginWrite().putBoolean(USER_CONFIRMED_MNEMONIC, userConfirmedMnemonic).commit();
  }

  /**
   * Consider using {@link #getPaymentsAvailability} which includes feature flag and region status.
   */
  public boolean mobileCoinPaymentsEnabled() {
    KeyValueReader reader = getStore().beginRead();

    return reader.getBoolean(MOB_PAYMENTS_ENABLED, false);
  }

  /**
   * Applies feature flags and region restrictions to return an enum which describes the available feature set for the user.
   */
  public PaymentsAvailability getPaymentsAvailability() {
    Context context = ApplicationDependencies.getApplication();

    if (!TextSecurePreferences.isPushRegistered(context) ||
        !GeographicalRestrictions.e164Allowed(TextSecurePreferences.getLocalNumber(context)))
    {
      return PaymentsAvailability.NOT_IN_REGION;
    }

    if (FeatureFlags.payments()) {
      if (mobileCoinPaymentsEnabled()) {
        return PaymentsAvailability.WITHDRAW_AND_SEND;
      } else {
        return PaymentsAvailability.REGISTRATION_AVAILABLE;
      }
    } else {
      if (mobileCoinPaymentsEnabled()) {
        return PaymentsAvailability.WITHDRAW_ONLY;
      } else {
        return PaymentsAvailability.DISABLED_REMOTELY;
      }
    }
  }

  @WorkerThread
  public void setMobileCoinPaymentsEnabled(boolean isMobileCoinPaymentsEnabled) {
    if (mobileCoinPaymentsEnabled() == isMobileCoinPaymentsEnabled) {
      return;
    }

    if (isMobileCoinPaymentsEnabled) {
      Entropy entropy = getPaymentsEntropy();
      if (entropy == null) {
        entropy = Entropy.generateNew();
        Log.i(TAG, "Generated new payments entropy");
      }

      getStore().beginWrite()
                .putBlob(PAYMENTS_ENTROPY, entropy.getBytes())
                .putBoolean(MOB_PAYMENTS_ENABLED, true)
                .putString(PAYMENTS_CURRENT_CURRENCY, currentCurrency().getCurrencyCode())
                .commit();
    } else {
      getStore().beginWrite()
                .putBoolean(MOB_PAYMENTS_ENABLED, false)
                .putBoolean(USER_CONFIRMED_MNEMONIC, false)
                .commit();
    }

    DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).markNeedsSync(Recipient.self().getId());
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  public @NonNull Mnemonic getPaymentsMnemonic() {
    Entropy paymentsEntropy = getPaymentsEntropy();
    if (paymentsEntropy == null) {
      throw new IllegalStateException("Entropy has not been set");
    }

    return paymentsEntropy.asMnemonic();
  }

  /**
   * True if a local entropy is set, regardless of whether payments is currently enabled.
   */
  public boolean hasPaymentsEntropy() {
    return getPaymentsEntropy() != null;
  }

  /**
   * Returns the local payments entropy, regardless of whether payments is currently enabled.
   * <p>
   * And null if has never been set.
   */
  public @Nullable Entropy getPaymentsEntropy() {
    return Entropy.fromBytes(getStore().getBlob(PAYMENTS_ENTROPY, null));
  }

  public @NonNull Balance mobileCoinLatestBalance() {
    return mobileCoinLatestFullLedger().getBalance();
  }

  public @NonNull LiveData<MobileCoinLedgerWrapper> liveMobileCoinLedger() {
    return liveMobileCoinLedger;
  }

  public @NonNull LiveData<Balance> liveMobileCoinBalance() {
    return liveMobileCoinBalance;
  }

  public void setCurrentCurrency(@NonNull Currency currentCurrency) {
    getStore().beginWrite()
              .putString(PAYMENTS_CURRENT_CURRENCY, currentCurrency.getCurrencyCode())
              .commit();

    liveCurrentCurrency.postValue(currentCurrency);
  }

  public @NonNull Currency currentCurrency() {
    String currencyCode = getStore().getString(PAYMENTS_CURRENT_CURRENCY, null);
    return currencyCode == null ? determineCurrency()
                                : Currency.getInstance(currencyCode);
  }

  public @NonNull MutableLiveData<Currency> liveCurrentCurrency() {
    return liveCurrentCurrency;
  }

  public boolean showAboutMobileCoinInfoCard() {
    return getStore().getBoolean(SHOW_ABOUT_MOBILE_COIN_INFO_CARD, true);
  }

  public boolean showAddingToYourWalletInfoCard() {
    return getStore().getBoolean(SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD, true);
  }

  public boolean showCashingOutInfoCard() {
    return getStore().getBoolean(SHOW_CASHING_OUT_INFO_CARD, true);
  }

  public boolean showRecoveryPhraseInfoCard() {
    if (userHasLargeBalance()) {
      return getStore().getBoolean(SHOW_CASHING_OUT_INFO_CARD, true);
    } else {
      return false;
    }
  }

  public boolean showUpdatePinInfoCard() {
    if (userHasLargeBalance()                  &&
        SignalStore.kbsValues().hasPin()       &&
        !SignalStore.kbsValues().hasOptedOut() &&
        SignalStore.pinValues().getKeyboardType().equals(PinKeyboardType.NUMERIC)) {
      return getStore().getBoolean(SHOW_CASHING_OUT_INFO_CARD, true);
    } else {
      return false;
    }
  }

  public void dismissAboutMobileCoinInfoCard() {
    getStore().beginWrite()
              .putBoolean(SHOW_ABOUT_MOBILE_COIN_INFO_CARD, false)
              .apply();
  }

  public void dismissAddingToYourWalletInfoCard() {
    getStore().beginWrite()
              .putBoolean(SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD, false)
              .apply();
  }

  public void dismissCashingOutInfoCard() {
    getStore().beginWrite()
              .putBoolean(SHOW_CASHING_OUT_INFO_CARD, false)
              .apply();
  }

  public void dismissRecoveryPhraseInfoCard() {
    getStore().beginWrite()
              .putBoolean(SHOW_RECOVERY_PHRASE_INFO_CARD, false)
              .apply();
  }

  public void dismissUpdatePinInfoCard() {
    getStore().beginWrite()
              .putBoolean(SHOW_UPDATE_PIN_INFO_CARD, false)
              .apply();
  }

  public void setMobileCoinFullLedger(@NonNull MobileCoinLedgerWrapper ledger) {
    getStore().beginWrite()
              .putBlob(MOB_LEDGER, ledger.serialize())
              .commit();

    liveMobileCoinLedger.postValue(ledger);
  }

  public @NonNull MobileCoinLedgerWrapper mobileCoinLatestFullLedger() {
    byte[] blob = getStore().getBlob(MOB_LEDGER, null);

    if (blob == null) {
      return new MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance());
    }

    try {
      return new MobileCoinLedgerWrapper(MobileCoinLedger.parseFrom(blob));
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, "Bad cached ledger, clearing", e);
      setMobileCoinFullLedger(new MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance()));
      throw new AssertionError(e);
    }
  }

  private @NonNull Currency determineCurrency() {
    String localE164 = TextSecurePreferences.getLocalNumber(ApplicationDependencies.getApplication());
    if (localE164 == null) {
      localE164 = "";
    }
    return Util.firstNonNull(CurrencyUtil.getCurrencyByE164(localE164),
                             CurrencyUtil.getCurrencyByLocale(Locale.getDefault()),
                             Currency.getInstance(DEFAULT_CURRENCY_CODE));
  }

  public void setEnabledAndEntropy(boolean enabled, @Nullable Entropy entropy) {
    KeyValueStore.Writer writer = getStore().beginWrite();

    if (entropy != null) {
      writer.putBlob(PAYMENTS_ENTROPY, entropy.getBytes());
    }

    writer.putBoolean(MOB_PAYMENTS_ENABLED, enabled)
          .commit();

    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public WalletRestoreResult restoreWallet(@NonNull String mnemonic) {
    byte[] entropyFromMnemonic;
    try {
      entropyFromMnemonic = Mnemonics.bip39EntropyFromMnemonic(mnemonic);
    } catch (BadMnemonicException e) {
      return WalletRestoreResult.MNEMONIC_ERROR;
    }
    Entropy paymentsEntropy = getPaymentsEntropy();
    if (paymentsEntropy != null) {
      byte[] existingEntropy = paymentsEntropy.getBytes();
      if (Arrays.equals(existingEntropy, entropyFromMnemonic)) {
        setMobileCoinPaymentsEnabled(true);
        setUserConfirmedMnemonic(true);
        return WalletRestoreResult.ENTROPY_UNCHANGED;
      }
    }

    getStore().beginWrite()
              .putBlob(PAYMENTS_ENTROPY, entropyFromMnemonic)
              .putBoolean(MOB_PAYMENTS_ENABLED, true)
              .remove(MOB_LEDGER)
              .putBoolean(USER_CONFIRMED_MNEMONIC, true)
              .commit();

    liveMobileCoinLedger.postValue(new MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance()));

    StorageSyncHelper.scheduleSyncForDataChange();

    return WalletRestoreResult.ENTROPY_CHANGED;
  }

  public enum WalletRestoreResult {
    ENTROPY_CHANGED,
    ENTROPY_UNCHANGED,
    MNEMONIC_ERROR
  }

  private boolean userHasLargeBalance() {
    return mobileCoinLatestBalance().getFullAmount().requireMobileCoin().greaterThan(LARGE_BALANCE_THRESHOLD);
  }
}
