package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.IOException;

public class LocationSlide extends ImageSlide {

    @NonNull
    private final SignalPlace place;

    public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull SignalPlace place) throws IOException, BitmapDecodingException {
        super(context, uri);
        this.place = place;
    }


    @NonNull
    public SignalPlace getPlace() {
        return place;
    }

}