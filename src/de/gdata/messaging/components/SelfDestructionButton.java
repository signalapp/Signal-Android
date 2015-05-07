package de.gdata.messaging.components;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;


import de.gdata.messaging.selfdestruction.DestroyOption;
import de.gdata.messaging.selfdestruction.SelfDestOptions;

public class SelfDestructionButton extends ImageButton {
    private SelfDestOptions SelfDestOptions;

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
}

