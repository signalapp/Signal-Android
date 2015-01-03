package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.TransportOptions.OnTransportChangedListener;

public class TransportButton extends ImageButtonDivet {
  private TransportOptions transportOptions;
  private EditText         composeText;

  @SuppressWarnings("unused")
  public TransportButton(Context context) {
    super(context);
    initialize();
  }

  @SuppressWarnings("unused")
  public TransportButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @SuppressWarnings("unused")
  public TransportButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  private void initialize() {
    transportOptions = new TransportOptions(getContext());
    transportOptions.addOnTransportChangedListener(new OnTransportChangedListener() {
      @Override
      public void onChange(TransportOption newTransport) {
        setImageResource(newTransport.drawableButtonIcon);
        setContentDescription(newTransport.composeHint);
        if (composeText != null) setComposeTextHint(newTransport.composeHint);
        // Check the number of enabled transports
        if(transportOptions.getEnabledTransports().size() > 1){
          setClickable(true);
          setDivetPosition(1);
        } else {
          setClickable(false);
          setDivetPosition(0);
        }
      }
    });

    setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (transportOptions.getEnabledTransports().size() > 1) {
          transportOptions.showPopup(TransportButton.this);
        }
      }
    });
  }

  public void setComposeTextView(EditText composeText) {
    this.composeText = composeText;
  }

  public TransportOption getSelectedTransport() {
    return transportOptions.getSelectedTransport();
  }

  public TransportOptions getTransportOptions() {
    return transportOptions;
  }

  public void initializeAvailableTransports(boolean isMediaMessage) {
    transportOptions.initializeAvailableTransports(isMediaMessage);
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
