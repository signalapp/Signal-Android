package org.thoughtcrime.securesms.payments.preferences.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.ArrayList;
import java.util.List;

public class InfoCard implements MappingModel<InfoCard> {

  private final @StringRes   int      titleId;
  private final @StringRes   int      messageId;
  private final @StringRes   int      actionId;
  private final @DrawableRes int      iconId;
  private final              Type     type;
  private final              Runnable dismiss;

  private InfoCard(@StringRes int titleId,
                   @StringRes int messageId,
                   @StringRes int actionId,
                   @DrawableRes int iconId,
                   @NonNull Type type,
                   @NonNull Runnable dismiss)
  {
    this.titleId   = titleId;
    this.messageId = messageId;
    this.actionId  = actionId;
    this.iconId    = iconId;
    this.type      = type;
    this.dismiss   = dismiss;
  }

  public @StringRes int getTitleId() {
    return titleId;
  }

  public @StringRes int getMessageId() {
    return messageId;
  }

  public @StringRes int getActionId() {
    return actionId;
  }

  public @NonNull Type getType() {
    return type;
  }

  public @DrawableRes int getIconId() {
    return iconId;
  }

  public void dismiss() {
    dismiss.run();
  }

  @Override
  public boolean areItemsTheSame(@NonNull InfoCard newItem) {
    return newItem.type == type;
  }

  @Override
  public boolean areContentsTheSame(@NonNull InfoCard newItem) {
    return newItem.titleId == titleId     &&
           newItem.messageId == messageId &&
           newItem.actionId == actionId   &&
           newItem.iconId == iconId       &&
           newItem.type == type;
  }

  public static @NonNull List<InfoCard> getInfoCards() {
    List<InfoCard> infoCards      = new ArrayList<>(Type.values().length);
    PaymentsValues paymentsValues = SignalStore.payments();

    if (!paymentsValues.isMnemonicConfirmed() && paymentsValues.hasPaymentsEntropy()) {
      infoCards.add(new InfoCard(R.string.payment_info_card_save_recovery_phrase,
                                 R.string.payment_info_card_your_recovery_phrase_gives_you,
                                 R.string.payment_info_card_save_your_phrase,
                                 R.drawable.ic_payments_info_card_restore_80,
                                 Type.RECORD_RECOVERY_PHASE,
                                 paymentsValues::dismissRecoveryPhraseInfoCard));
    }

    if (paymentsValues.showUpdatePinInfoCard()) {
      infoCards.add(new InfoCard(R.string.payment_info_card_update_your_pin,
                                 R.string.payment_info_card_with_a_high_balance,
                                 R.string.payment_info_card_update_pin,
                                 R.drawable.ic_payments_info_card_pin_80,
                                 Type.UPDATE_YOUR_PIN,
                                 paymentsValues::dismissUpdatePinInfoCard));
    }

    if (paymentsValues.showAboutMobileCoinInfoCard()) {
      infoCards.add(new InfoCard(R.string.payment_info_card_about_mobilecoin,
                                 R.string.payment_info_card_mobilecoin_is_a_new_privacy_focused_digital_currency,
                                 R.string.LearnMoreTextView_learn_more,
                                 R.drawable.ic_about_mc_80,
                                 Type.ABOUT_MOBILECOIN,
                                 paymentsValues::dismissAboutMobileCoinInfoCard));
    }

    if (paymentsValues.showAddingToYourWalletInfoCard()) {
      infoCards.add(new InfoCard(R.string.payment_info_card_adding_funds,
                                 R.string.payment_info_card_you_can_add_funds_for_use_in,
                                 R.string.LearnMoreTextView_learn_more,
                                 R.drawable.ic_add_money_80,
                                 Type.ADDING_TO_YOUR_WALLET,
                                 paymentsValues::dismissAddingToYourWalletInfoCard));
    }

    if (paymentsValues.showCashingOutInfoCard()) {
      infoCards.add(new InfoCard(R.string.payment_info_card_cashing_out,
                                 R.string.payment_info_card_you_can_cash_out_mobilecoin,
                                 R.string.LearnMoreTextView_learn_more,
                                 R.drawable.ic_cash_out_80,
                                 Type.CASHING_OUT,
                                 paymentsValues::dismissCashingOutInfoCard));
    }

    return infoCards;
  }

  public enum Type {
    RECORD_RECOVERY_PHASE,
    UPDATE_YOUR_PIN,
    ABOUT_MOBILECOIN,
    ADDING_TO_YOUR_WALLET,
    CASHING_OUT
  }
}
