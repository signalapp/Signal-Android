package de.gdata.messaging.components;


import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;


import org.thoughtcrime.securesms.R;

import de.gdata.messaging.selfdestruction.DestroyOption;
import de.gdata.messaging.selfdestruction.SelfDestOptions;

public class SelfDestructionButton extends ImageButton {
    private SelfDestOptions SelfDestOptions;
    private EditText composeText;
    private SelectTransportButton selectTransportButtonReference;

    @SuppressWarnings("unused")
    public SelfDestructionButton(Context context) {
        super(context);
        initialize();
    }

    @SuppressWarnings("unused")
    public SelfDestructionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }
    public void setComposeTextView(EditText composeText) {
        this.composeText = composeText;
    }

    @SuppressWarnings("unused")
    public SelfDestructionButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        SelfDestOptions = new SelfDestOptions(getContext());
        SelfDestOptions.setOnSelfDestChangedListener(new SelfDestOptions.onDestroyTimeChangedListener() {
            @Override
            public void onChange(DestroyOption newTransport) {
                setImageResource(newTransport.drawable);
                if (Integer.parseInt(newTransport.key) > 0) {
                    if (composeText != null)
                        setComposeTextHint(getResources().getString(R.string.self_destruction_compose_hint));
                } else {
                    setImageResource(R.drawable.ic_action_timebomb);
                    if (composeText != null) {
                        setComposeTextHint(selectTransportButtonReference.getSelectedTransport().composeHint);
                    }
                }
            }
        });

        setHapticFeedbackEnabled(false);

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (SelfDestOptions.getEnabledSelfDest().size() > 1) {
                    SelfDestOptions.showPopup(SelfDestructionButton.this);
                    return true;
                }
                return false;
            }
        });
    }
    public DestroyOption getSelectedSelfDestTime() {
        return SelfDestOptions.getSelectedSelfDestTime();
    }

    public void initializeAvailableSelfDests() {
        SelfDestOptions.initializeAvailableSelfDests();
    }

    public void disableSelfDest(String transport) {
        SelfDestOptions.disableSelfDest(transport);
    }

    public void setDefaultSelfDest(String transport) {
        SelfDestOptions.setDefaultSelfDest(transport);
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
    public void setSelectTransportButtonReference(SelectTransportButton pSelectTransportButtonReference) {
        selectTransportButtonReference = pSelectTransportButtonReference;
    }
}

