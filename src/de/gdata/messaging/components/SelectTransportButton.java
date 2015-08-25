package de.gdata.messaging.components;


import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.EncryptedCharacterCalculator;
import org.thoughtcrime.securesms.util.PushCharacterCalculator;
import org.thoughtcrime.securesms.util.SmsCharacterCalculator;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SelectTransportButton extends ImageButton {
    private TransportOptions transportOptions;
    private EditText composeText;
    private SelfDestructionButton destroyButtonReference;

    @SuppressWarnings("unused")
    public SelectTransportButton(Context context) {
        super(context);
        initialize();
    }

    @SuppressWarnings("unused")
    public SelectTransportButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    @SuppressWarnings("unused")
    public SelectTransportButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }
    private String handleInviteLink() {
        try {
            boolean a = SecureRandom.getInstance("SHA1PRNG").nextBoolean();
            if (a)
                return getContext().getString(R.string.ConversationActivity_get_with_it, getContext().getString(R.string.conversation_activity_invite_link));
            else
                return getContext().getString(R.string.ConversationActivity_install_textsecure, getContext().getString(R.string.conversation_activity_invite_link));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
    private void initialize() {
        transportOptions = new TransportOptions(getContext());
        transportOptions.setOnTransportChangedListener(new TransportOptions.OnTransportChangedListener() {
            @Override
            public void onChange(TransportOption newTransport) {
                setImageResource(newTransport.drawable);
                setContentDescription(newTransport.composeHint);
                if (composeText != null && !((composeText.getHint() + "").contains(getResources().getString(R.string.self_destruction_compose_hint)))) {
                    setComposeTextHint(newTransport.composeHint);
                }
                if (newTransport.key.contains("insecure_sms")) {
                    destroyButtonReference.setVisibility(View.GONE);
                    destroyButtonReference.setEnabled(false);
                    setComposeTextHint(newTransport.composeHint);
                } else if(!newTransport.key.contains("invite")){
                    destroyButtonReference.setVisibility(View.VISIBLE);
                    destroyButtonReference.setEnabled(true);
                }
                if(newTransport.key.contains("invite")) {
                    setComposeText(handleInviteLink());
                }
                if (newTransport.isForcedSms()) {
                    if (newTransport.isForcedPlaintext()) {
                        ConversationActivity.characterCalculator = new SmsCharacterCalculator();
                    } else {
                        ConversationActivity.characterCalculator = new EncryptedCharacterCalculator();
                    }
                } else {
                    ConversationActivity.characterCalculator = new PushCharacterCalculator();
                }
            }
        });

        setHapticFeedbackEnabled(false);

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (transportOptions.getEnabledTransports().size() > 1) {
                    transportOptions.showPopup((View) getParent());
                    return true;
                }
                return false;
            }
        });
    }

    public void setComposeTextView(EditText composeText) {
        this.composeText = composeText;
    }
    public TransportOption getSelectedTransport() {
        return transportOptions.getSelectedTransport();
    }

    public void initializeAvailableTransports(boolean isMediaMessage, boolean isGroupOrEncryptedConversation) {
        transportOptions.initializeAvailableTransports(isMediaMessage, isGroupOrEncryptedConversation);
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
            setComposeText("");
            SpannableString span = new SpannableString(hint);
            span.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            this.composeText.setHint(span);
        }
    }
    private void setComposeText(String text) {
        if (text == null) {
            this.composeText.setHint(null);
        } else {
            SpannableString span = new SpannableString(text);
            span.setSpan(new RelativeSizeSpan(0.8f), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            this.composeText.setText(span);
        }
    }
    public void setDestroyButtonReference(SelfDestructionButton pDestroyButtonReference) {
        destroyButtonReference = pDestroyButtonReference;
    }
}

