package org.thoughtcrime.securesms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.TextView;

public class WebRtcCallVolumeActivity extends AppCompatActivity {

    private static final String TAG = WebRtcCallVolumeActivity.class.getSimpleName();

    private TextView mVolumeIndexView;
    private AudioManager mAudioManager;
    private int mVolume = 5;
    private int mMaxVolume = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webrtc_call_volume);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        mVolumeIndexView = findViewById(R.id.volume_index);
        mVolumeIndexView.setText("" + mVolume);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mVolume < mMaxVolume) {
                    mVolume++;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mVolume > 1) {
                    mVolume--;
                }
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        setVolume(mVolume);
        mVolumeIndexView.setText("" + mVolume);
        return true;
    }

    private void setVolume(int newVolume) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newVolume,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
            Settings.System.putInt(this.getContentResolver(), "volume_voice", newVolume);
    }
}

