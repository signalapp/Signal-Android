package org.thoughtcrime.securesms.payments.confirm;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.confirm.ConfirmPaymentState.Status;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

public class ConfirmPaymentAdapter extends MappingAdapter {
  public ConfirmPaymentAdapter(@NonNull Callbacks callbacks) {
    registerFactory(LoadingItem.class, LoadingItemViewHolder::new, R.layout.confirm_payment_loading_item);
    registerFactory(LineItem.class, LineItemViewHolder::new, R.layout.confirm_payment_line_item);
    registerFactory(Divider.class, MappingViewHolder.SimpleViewHolder::new, R.layout.confirm_payment_divider);
    registerFactory(TotalLineItem.class, TotalLineItemViewHolder::new, R.layout.confirm_payment_total_line_item);
    registerFactory(ConfirmPaymentStatus.class, p -> new ConfirmPaymentViewHolder(p, callbacks), R.layout.confirm_payment_status);
  }

  public interface Callbacks {
    void onConfirmPayment();
  }

  public static class LoadingItem implements MappingModel<LoadingItem> {
    @Override
    public boolean areItemsTheSame(@NonNull LoadingItem newItem) {
      return true;
    }

    @Override
    public boolean areContentsTheSame(@NonNull LoadingItem newItem) {
      return true;
    }
  }

  public static class LineItem implements MappingModel<LineItem> {

    private final CharSequence description;
    private final String       value;

    public LineItem(@NonNull CharSequence description, @NonNull String value) {
      this.description = description;
      this.value       = value;
    }

    public @NonNull CharSequence getDescription() {
      return description;
    }

    public @NonNull String getValue() {
      return value;
    }

    @Override
    public boolean areItemsTheSame(@NonNull LineItem newItem) {
      return description.toString().equals(newItem.description.toString());
    }

    @Override
    public boolean areContentsTheSame(@NonNull LineItem newItem) {
      return value.equals(newItem.value);
    }
  }

  public static class TotalLineItem implements MappingModel<TotalLineItem> {
    private final LineItem lineItem;

    public TotalLineItem(@NonNull String description, @NonNull String value) {
      this.lineItem = new LineItem(description, value);
    }

    public @NonNull LineItem getLineItem() {
      return lineItem;
    }

    @Override
    public boolean areItemsTheSame(@NonNull TotalLineItem newItem) {
      return lineItem.areItemsTheSame(newItem.lineItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull TotalLineItem newItem) {
      return lineItem.areContentsTheSame(newItem.lineItem);
    }
  }

  public static class ConfirmPaymentStatus implements MappingModel<ConfirmPaymentStatus> {
    private final Status                        status;
    private final ConfirmPaymentState.FeeStatus feeStatus;
    private final Money                         balance;

    public ConfirmPaymentStatus(@NonNull Status status, @NonNull ConfirmPaymentState.FeeStatus feeStatus, @NonNull Money balance) {
      this.status    = status;
      this.feeStatus = feeStatus;
      this.balance   = balance;
    }

    public int getConfirmPaymentVisibility() {
      return status == Status.CONFIRM ? View.VISIBLE : View.INVISIBLE;
    }

    public boolean getConfirmPaymentEnabled() {
      return status == Status.CONFIRM &&
             feeStatus == ConfirmPaymentState.FeeStatus.SET;
    }

    public @NonNull Status getStatus() {
      return status;
    }

    public @NonNull CharSequence getInfoText(@NonNull Context context) {
      switch (status) {
        case CONFIRM:    return context.getString(R.string.ConfirmPayment__balance_s, balance.toString(FormatterOptions.defaults()));
        case SUBMITTING: return context.getString(R.string.ConfirmPayment__submitting_payment);
        case PROCESSING: return context.getString(R.string.ConfirmPayment__processing_payment);
        case DONE:       return context.getString(R.string.ConfirmPayment__payment_complete);
        case ERROR:      return SpanUtil.color(ContextCompat.getColor(context, R.color.signal_alert_primary), context.getString(R.string.ConfirmPayment__payment_failed));
        case TIMEOUT:    return context.getString(R.string.ConfirmPayment__payment_will_continue_processing);
      }
      throw new AssertionError();
    }

    @Override
    public boolean areItemsTheSame(@NonNull ConfirmPaymentStatus newItem) {
      return true;
    }

    @Override
    public boolean areContentsTheSame(@NonNull ConfirmPaymentStatus newItem) {
      return status == newItem.status &&
             feeStatus == newItem.feeStatus &&
             balance.equals(newItem.balance);
    }
  }

  public static final class Divider implements MappingModel<Divider> {
    @Override
    public boolean areItemsTheSame(@NonNull Divider newItem) {
      return true;
    }

    @Override
    public boolean areContentsTheSame(@NonNull Divider newItem) {
      return true;
    }
  }

  public static final class LoadingItemViewHolder extends MappingViewHolder<LoadingItem> {

    public LoadingItemViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override public void bind(@NonNull LoadingItem model) {
    }
  }

  public static final class LineItemViewHolder extends MappingViewHolder<LineItem> {
    private final TextView description;
    private final TextView value;

    public LineItemViewHolder(@NonNull View itemView) {
      super(itemView);
      this.description = findViewById(R.id.confirm_payment_line_item_description);
      this.value       = findViewById(R.id.confirm_payment_line_item_value);
    }

    @Override
    public void bind(@NonNull LineItem model) {
      description.setText(model.getDescription());
      value.setText(model.getValue());
    }
  }

  private static class TotalLineItemViewHolder extends MappingViewHolder<TotalLineItem> {
    private final LineItemViewHolder delegate;

    public TotalLineItemViewHolder(@NonNull View itemView) {
      super(itemView);
      this.delegate = new LineItemViewHolder(itemView);
    }

    @Override
    public void bind(@NonNull TotalLineItem model) {
      delegate.bind(model.getLineItem());
    }
  }

  private static class ConfirmPaymentViewHolder extends MappingViewHolder<ConfirmPaymentStatus> {

    private final View                confirmPayment;
    private final LottieAnimationView inProgress;
    private final LottieAnimationView completed;
    private final LottieAnimationView failed;
    private final LottieAnimationView timeout;
    private final TextView            infoText;
    private final Callbacks           callbacks;

    public ConfirmPaymentViewHolder(@NonNull View itemView, @NonNull Callbacks callbacks) {
      super(itemView);
      this.callbacks      = callbacks;
      this.confirmPayment = findViewById(R.id.confirm_payment_status_confirm);
      this.inProgress     = findViewById(R.id.confirm_payment_spinner_lottie);
      this.completed      = findViewById(R.id.confirm_payment_success_lottie);
      this.failed         = findViewById(R.id.confirm_payment_error_lottie);
      this.timeout        = findViewById(R.id.confirm_payment_timeout_lottie);
      this.infoText       = findViewById(R.id.confirm_payment_status_info);
    }

    @Override
    public void bind(@NonNull ConfirmPaymentStatus model) {
      confirmPayment.setOnClickListener(v -> callbacks.onConfirmPayment());
      confirmPayment.setVisibility(model.getConfirmPaymentVisibility());
      confirmPayment.setEnabled(model.getConfirmPaymentEnabled());
      infoText.setText(model.getInfoText(getContext()));

      switch (model.getStatus()) {
        case CONFIRM:
          break;
        case SUBMITTING:
        case PROCESSING:
          playNextAnimation(inProgress, completed, failed, timeout);
          break;
        case DONE:
          playNextAnimation(completed, inProgress, failed, timeout);
          break;
        case ERROR:
          playNextAnimation(failed, inProgress, completed, timeout);
          break;
        case TIMEOUT:
          playNextAnimation(timeout, inProgress, completed, failed);
          break;
      }
    }

    private static void playNextAnimation(@NonNull LottieAnimationView next,
                                          @NonNull LottieAnimationView ... hide)
    {
      for (LottieAnimationView lottieAnimationView : hide) {
        lottieAnimationView.setVisibility(View.INVISIBLE);
      }
      
      next.setVisibility(View.VISIBLE);
      next.post(() -> {
        if (!next.isAnimating()) {
          next.playAnimation();
        }
      });
    }
  }
}
