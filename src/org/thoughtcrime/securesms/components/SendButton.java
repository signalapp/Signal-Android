package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.TransportOptions.OnTransportChangedListener;

public class SendButton extends ImageButton {
  private TransportOptions transportOptions;
  private EditText         composeText;

  @SuppressWarnings("unused")
  public SendButton(Context context) {
    super(context);
    initialize();
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  private void initialize() {
    transportOptions = new TransportOptions(getContext());
    transportOptions.setOnTransportChangedListener(new OnTransportChangedListener() {
      @Override
      public void onChange(TransportOption newTransport) {
        setImageResource(newTransport.drawable);
        setContentDescription(newTransport.composeHint);
        if (composeText != null) setComposeTextHint(newTransport.composeHint);
      }
    });
  }

  public void setComposeTextView(EditText composeText) {
    this.composeText = composeText;
  }

  public TransportOption getSelectedTransport() {
    return transportOptions.getSelectedTransport();
  }

  public void initializeAvailableTransports(boolean isMediaMessage, boolean isEncryptedOrGroupMessage) {
    transportOptions.initializeAvailableTransports(isMediaMessage, isEncryptedOrGroupMessage);
  }

  public void disableTransport(String transport) {
    transportOptions.disableTransport(transport);
  }

  public void setDefaultTransport(String transport) {
    transportOptions.setDefaultTransport(transport);
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
