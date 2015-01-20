package de.gdata.messaging.util;

/**
 * Created by jan on 20.01.15.
 */

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import java.util.Hashtable;

/**
 * Container class for application fonts to be used
 */
public class TypeFaces {
    private static final Hashtable<String, Typeface> cache = new Hashtable<String, Typeface>();

    public static Typeface getTypeFace(Context context, String assetPath) {
        synchronized (cache) {
            if (!cache.containsKey(assetPath)) {
                try {
                    Typeface typeFace = Typeface.createFromAsset(context.getAssets(), assetPath);

                    cache.put(assetPath, typeFace);

                } catch (Exception e) {
                   Log.d("FONT", e.toString());

                    return null;
                }
            }
            return cache.get(assetPath);
        }
    }
}
