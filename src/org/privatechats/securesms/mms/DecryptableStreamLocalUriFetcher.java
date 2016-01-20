package org.thoughtcrime.securesms.mms;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class DecryptableStreamLocalUriFetcher extends StreamLocalUriFetcher {

  private static final String TAG = DecryptableStreamLocalUriFetcher.class.getSimpleName();

  private Context      context;
  private MasterSecret masterSecret;

  public DecryptableStreamLocalUriFetcher(Context context, MasterSecret masterSecret, Uri uri) {
    super(context, uri);
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
    try {
      return PartAuthority.getAttachmentStream(context, masterSecret, uri);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("PartAuthority couldn't load Uri resource.");
    }
  }
}
