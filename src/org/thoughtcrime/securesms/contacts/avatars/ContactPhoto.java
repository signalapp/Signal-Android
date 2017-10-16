package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;


import com.bumptech.glide.load.Key;

import java.io.IOException;
import java.io.InputStream;

public interface ContactPhoto extends Key {

  InputStream openInputStream(Context context) throws IOException;

}
