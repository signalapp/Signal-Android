package org.thoughtcrime.securesms.database;

import android.content.Context;

import org.thoughtcrime.securesms.R;

public class VideoDraft extends Draft {

    public VideoDraft(String value) {
        super(Draft.VIDEO, value);
    }

    @Override
    public String getSnippet(Context context) {
        return context.getString(R.string.DraftDatabase_Draft_video_snippet);
    }
}
