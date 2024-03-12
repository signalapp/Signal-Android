package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A page that sits in the {@link MediaSendFragmentPagerAdapter}.
 */
public interface MediaSendPageFragment {

  @NonNull Uri getUri();

  void setUri(@NonNull Uri uri);

  @Nullable Object saveState();

  void restoreState(@NonNull Object state);

  void notifyHidden();
}
