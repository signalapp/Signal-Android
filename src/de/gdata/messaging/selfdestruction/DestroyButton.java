package de.gdata.messaging.selfdestruction;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

public class DestroyButton extends ImageButton {
  private SelfDestOptions destroyOptions;
  private EditText composeText;

  @SuppressWarnings("unused")
  public DestroyButton(Context context) {
    super(context);
    initialize();
  }

  @SuppressWarnings("unused")
  public DestroyButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @SuppressWarnings("unused")
  public DestroyButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  private void initialize() {
    destroyOptions = new SelfDestOptions(getContext());
    destroyOptions.setOnSelfDestChangedListener(new SelfDestOptions.onDestroyTimeChangedListener() {
        @Override
        public void onChange(DestroyOption destroyOption) {
            setImageResource(destroyOption.drawable);
            setContentDescription(destroyOption.composeHint);
            if (composeText != null) setComposeTextHint(destroyOption.composeHint);
        }
    });

    setOnLongClickListener(new OnLongClickListener() {
      @Override
      public boolean onLongClick(View view) {
        if (destroyOptions.getEnabledSelfDest().size() > 1) {
          destroyOptions.showPopup(DestroyButton.this);
          return true;
        }
        return false;
      }
    });
  }

  public void setComposeTextView(EditText composeText) {
    this.composeText = composeText;
  }

  public DestroyOption getSelectedTransport() {
    return destroyOptions.getSelectedSelfDestTime();
  }

  public void initializeAvailableTransports() {
    destroyOptions.initializeAvailableSelfDests();
  }


  public void disableTransport(String transport) {
    destroyOptions.disableSelfDest(transport);
  }

  public void setDefaultTransport(String transport) {
    destroyOptions.setDefaultSelfDest(transport);
  }

  private void setComposeTextHint(String hint) {
    if (hint == null) {
      this.composeText.setHint(null);
    } else {
      SpannableString span = new SpannableString(hint);
      span.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
      this.composeText.setHint(span);
    }
  }
}
