package org.thoughtcrime.securesms.database;

import android.content.Context;

public class TextDraft extends Draft {

    public TextDraft(String value) {
        super(Draft.TEXT, value);
    }

    @Override
    public String getSnippet(Context context) {
        return getValue();
    }
}
