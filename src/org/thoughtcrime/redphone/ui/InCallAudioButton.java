package org.thoughtcrime.redphone.ui;

import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import org.thoughtcrime.redphone.util.AudioUtils;
import org.thoughtcrime.securesms.R;

import static org.thoughtcrime.redphone.util.AudioUtils.AudioMode.DEFAULT;
import static org.thoughtcrime.redphone.util.AudioUtils.AudioMode.HEADSET;
import static org.thoughtcrime.redphone.util.AudioUtils.AudioMode.SPEAKER;

/**
 * Manages the audio button displayed on the in-call screen
 *
 * The behavior of this button depends on the availability of headset audio, and changes from being a regular
 * toggle button (enabling speakerphone) to bringing up a model dialog that includes speakerphone, bluetooth,
 * and regular audio options.
 *
 * Based on com.android.phone.InCallTouchUI
 *
 * @author Stuart O. Anderson
 */
public class InCallAudioButton {
  private static final String TAG = InCallAudioButton.class.getName();
//  private static final int HANDSET_ID = 0x10;
//  private static final int HEADSET_ID = 0x20;
//  private static final int SPEAKER_ID = 0x30;

  private final CompoundButton mAudioButton;
  private boolean headsetAvailable;
  private AudioUtils.AudioMode currentMode;
  private Context context;
  private CallControls.AudioButtonListener listener;

  public InCallAudioButton(CompoundButton audioButton) {
    mAudioButton = audioButton;

    currentMode = DEFAULT;
    headsetAvailable = false;

    updateView();
    setListener(new CallControls.AudioButtonListener() {
      @Override
      public void onAudioChange(AudioUtils.AudioMode mode) {
        //No Action By Default.
      }
    });
    context = audioButton.getContext();
  }

  public void setHeadsetAvailable(boolean available) {
    headsetAvailable = available;
    updateView();
  }

  public void setAudioMode(AudioUtils.AudioMode newMode) {
    currentMode = newMode;
    updateView();
  }

  private void updateView() {
    // The various layers of artwork for this button come from
    // redphone_btn_compound_audio.xmlaudio.xml.  Keep track of which layers we want to be
    // visible:
    //
    // - This selector shows the blue bar below the button icon when
    //   this button is a toggle *and* it's currently "checked".
    boolean showToggleStateIndication = false;
    //
    // - This is visible if the popup menu is enabled:
    boolean showMoreIndicator = false;
    //
    // - Foreground icons for the button.  Exactly one of these is enabled:
    boolean showSpeakerOnIcon = false;
    boolean showSpeakerOffIcon = false;
    boolean showHandsetIcon = false;
    boolean showHeadsetIcon = false;

    boolean speakerOn = currentMode == AudioUtils.AudioMode.SPEAKER;

    if (headsetAvailable) {
      mAudioButton.setEnabled(true);

      // The audio button is NOT a toggle in this state.  (And its
      // setChecked() state is irrelevant since we completely hide the
      // redphone_btn_compound_background layer anyway.)

      // Update desired layers:
      showMoreIndicator = true;
      Log.d(TAG, "UI Mode: " + currentMode);
      if (currentMode == AudioUtils.AudioMode.HEADSET) {
        showHeadsetIcon = true;
      } else if (speakerOn) {
        showSpeakerOnIcon = true;
      } else {
        showHandsetIcon = true;
      }
    } else {
      mAudioButton.setEnabled(true);

      mAudioButton.setChecked(speakerOn);
      showSpeakerOnIcon = speakerOn;
      showSpeakerOffIcon = !speakerOn;

      showToggleStateIndication = true;
    }

    final int HIDDEN = 0;
    final int VISIBLE = 255;

    LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();

    layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
      .setAlpha(showToggleStateIndication ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.moreIndicatorItem)
      .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.bluetoothItem)
      .setAlpha(showHeadsetIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.handsetItem)
      .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.speakerphoneOnItem)
      .setAlpha(showSpeakerOnIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.speakerphoneOffItem)
      .setAlpha(showSpeakerOffIcon ? VISIBLE : HIDDEN);

    mAudioButton.invalidate();
  }

  private void log(String msg) {
    Log.d(TAG, msg);
  }

  public void setListener(final CallControls.AudioButtonListener listener) {
    this.listener = listener;
    mAudioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if(headsetAvailable) {
          displayAudioChoiceDialog();
        } else {
          currentMode = b ? AudioUtils.AudioMode.SPEAKER : DEFAULT;
          listener.onAudioChange(currentMode);
          updateView();
        }
      }
    });
  }

  private void displayAudioChoiceDialog() {
//    MenuBuilder mb = new MenuBuilder(context);
//    mb.add(0, HANDSET_ID, 0, "Handset");
//    mb.add(0, HEADSET_ID, 0, "Headset");
//    mb.add(0, SPEAKER_ID, 0, "Speaker");
//    mb.setCallback(new AudioRoutingPopupListener());

//    View attachmentView = ((View) mAudioButton.getParent()).findViewById(R.id.menuAttachment);
    Log.w(TAG, "Displaying popup...");
    PopupMenu popupMenu = new PopupMenu(context, mAudioButton);
    popupMenu.getMenuInflater().inflate(R.menu.redphone_audio_popup_menu, popupMenu.getMenu());
    popupMenu.setOnMenuItemClickListener(new AudioRoutingPopupListener());
    popupMenu.show();
//    MenuPopupHelper mph = new MenuPopupHelper(context, mb, attachmentView);
//
//    mph.show();
  }

  private class AudioRoutingPopupListener implements PopupMenu.OnMenuItemClickListener {

//    @Override
//    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
//      switch (item.getItemId()) {
//        case HANDSET_ID:
//          currentMode = DEFAULT;
//          break;
//        case HEADSET_ID:
//          currentMode = HEADSET;
//          break;
//        case SPEAKER_ID:
//          currentMode = SPEAKER;
//          break;
//        default:
//          Log.w(TAG, "Unknown item selected in audio popup menu: " + item.toString());
//      }
//      Log.d(TAG, "Selected: " + currentMode + " -- " + item.getItemId());
//
//      listener.onAudioChange(currentMode);
//      updateView();
//      return true;
//    }
//
//    @Override
//    public void onMenuModeChange(MenuBuilder menu) {
//      //To change body of implemented methods use File | Settings | File Templates.
//    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.handset:
          currentMode = DEFAULT;
          break;
        case R.id.headset:
          currentMode = HEADSET;
          break;
        case R.id.speaker:
          currentMode = SPEAKER;
          break;
        default:
          Log.w(TAG, "Unknown item selected in audio popup menu: " + item.toString());
      }
      Log.d(TAG, "Selected: " + currentMode + " -- " + item.getItemId());

      listener.onAudioChange(currentMode);
      updateView();
      return true;
    }
  }
}
