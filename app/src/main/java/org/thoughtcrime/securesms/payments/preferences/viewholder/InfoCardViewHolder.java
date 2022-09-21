package org.thoughtcrime.securesms.payments.preferences.viewholder;

import android.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeAdapter;
import org.thoughtcrime.securesms.payments.preferences.model.InfoCard;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

public class InfoCardViewHolder extends MappingViewHolder<InfoCard> {

  private final Toolbar   toolbar;
  private final TextView  message;
  private final ImageView icon;
  private final TextView  learnMore;

  private final PaymentsHomeAdapter.Callbacks callbacks;

  public InfoCardViewHolder(@NonNull View itemView, @NonNull PaymentsHomeAdapter.Callbacks callbacks) {
    super(itemView);

    this.callbacks = callbacks;

    toolbar   = itemView.findViewById(R.id.payment_info_card_toolbar);
    message   = itemView.findViewById(R.id.payment_info_card_message);
    icon      = itemView.findViewById(R.id.payment_info_card_icon);
    learnMore = itemView.findViewById(R.id.payment_info_card_learn_more);

    learnMore.setMovementMethod(LinkMovementMethod.getInstance());
  }

  @Override
  public void bind(@NonNull InfoCard model) {
    toolbar.setTitle(model.getTitleId());

    toolbar.getMenu().clear();
    toolbar.inflateMenu(R.menu.payment_info_card_overflow);
    toolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == R.id.action_hide) {
        new AlertDialog.Builder(getContext())
            .setMessage(R.string.payment_info_card_hide_this_card)
            .setPositiveButton(R.string.payment_info_card_hide, (dialog, which) -> {
              model.dismiss();
              dialog.dismiss();
              callbacks.onInfoCardDismissed(model.getType());
            })
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .show();
        return true;
      }

      return false;
    });

    message.setText(model.getMessageId());
    icon.setImageDrawable(AppCompatResources.getDrawable(getContext(), model.getIconId()));

    SpannableString spannableString = new SpannableString(itemView.getContext().getString(model.getActionId()));
    spannableString.setSpan(getSpan(model.getType()), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    learnMore.setText(spannableString);
  }

  private ClickableSpan getSpan(@NonNull InfoCard.Type type) {
    switch (type) {
      case RECORD_RECOVERY_PHASE:
        return new CallbackSpan(callbacks::onViewRecoveryPhrase);
      case UPDATE_YOUR_PIN:
        return new CallbackSpan(callbacks::onUpdatePin);
      case ABOUT_MOBILECOIN:
        return new LearnMoreURLSpan(getContext().getString(R.string.payment_info_card__learn_more__about_mobilecoin));
      case ADDING_TO_YOUR_WALLET:
        return new LearnMoreURLSpan(getContext().getString(R.string.payment_info_card__learn_more__adding_to_your_wallet));
      case CASHING_OUT:
        return new LearnMoreURLSpan(getContext().getString(R.string.payment_info_card__learn_more__cashing_out));
    }

    throw new IllegalArgumentException("Unexpected type " + type.name());
  }

  private static final class CallbackSpan extends ClickableSpan {

    private final Runnable runnable;

    private CallbackSpan(@NonNull Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public void onClick(@NonNull View widget) {
      runnable.run();
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
      super.updateDrawState(ds);
      ds.setUnderlineText(false);
    }
  }

  private static final class LearnMoreURLSpan extends URLSpan {

    public LearnMoreURLSpan(String url) {
      super(url);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
      super.updateDrawState(ds);
      ds.setUnderlineText(false);
    }
  }
}
