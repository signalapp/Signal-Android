package org.thoughtcrime.securesms.giph.mp4;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.signal.core.util.DeviceProperties;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

/**
 * Central policy object for determining what kind of gifs to display, routing, etc.
 */
public final class GiphyMp4PlaybackPolicy {

  private GiphyMp4PlaybackPolicy() { }

  public static boolean autoplay() {
    return !DeviceProperties.isLowMemoryDevice(AppDependencies.getApplication()) && SignalStore.settings().isAutoplayStickersAndGifsEnabled();
  }

  public static int maxSimultaneousPlaybackInSearchResults() {
    return AppDependencies.getExoPlayerPool().getPoolStats().getMaxUnreserved();
  }

  public static int maxSimultaneousPlaybackInConversation() {
    return AppDependencies.getExoPlayerPool().getPoolStats().getMaxUnreserved() / 3;
  }
}
