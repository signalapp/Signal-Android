package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

/**
 * A page that sits in the {@link MediaSendFragmentPagerAdapter}.
 */
public interface MediaSendPageFragment {

  @NonNull Uri getUri();

  void setUri(@NonNull Uri uri);

  @Nullable View getPlaybackControls();

  @Nullable Object saveState();

  void restoreState(@NonNull Object state);
}
