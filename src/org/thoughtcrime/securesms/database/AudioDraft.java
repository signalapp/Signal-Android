package org.thoughtcrime.securesms.database;

import android.content.Context;

import org.thoughtcrime.securesms.R;

public class AudioDraft extends Draft {

    public AudioDraft(String value) {
        super(Draft.AUDIO, value);
    }

    @Override
    public String getSnippet(Context context) {
        return context.getString(R.string.DraftDatabase_Draft_audio_snippet);
    }
}
