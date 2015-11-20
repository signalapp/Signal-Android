package org.thoughtcrime.securesms.database;

import android.content.Context;

import org.thoughtcrime.securesms.R;

public abstract class Draft {
    public static final String TEXT  = "text";
    public static final String IMAGE = "image";
    public static final String VIDEO = "video";
    public static final String AUDIO = "audio";

    private final String type;
    private final String value;

    public Draft(String type, String value) {
        this.type  = type;
        this.value = value;
    }

    @Deprecated
    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public abstract String getSnippet(Context context);
}