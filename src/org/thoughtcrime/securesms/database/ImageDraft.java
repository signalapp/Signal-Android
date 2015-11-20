package org.thoughtcrime.securesms.database;

import android.content.Context;

import org.thoughtcrime.securesms.R;

public class ImageDraft extends Draft {

    public ImageDraft(String value) {
        super(Draft.IMAGE, value);
    }

    @Override
    public String getSnippet(Context context) {
        return context.getString(R.string.DraftDatabase_Draft_image_snippet);
    }
}
