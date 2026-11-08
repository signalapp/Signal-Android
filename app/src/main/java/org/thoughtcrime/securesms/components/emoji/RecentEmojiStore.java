package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.core.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class RecentEmojiStore {
  private static final String TAG            = Log.tag(RecentEmojiStore.class);
  private static final int    EMOJI_LRU_SIZE = 50;

  /** The shared-prefs entry this store persists its LRU into. */
  public static final String RECENT_STORAGE_KEY = "pref_recent_emoji2";

  private final SharedPreferences     prefs;
  private final String                preferenceName;
  private final LinkedHashSet<String> recentlyUsed;

  public RecentEmojiStore(Context context, @NonNull String preferenceName) {
    this.prefs          = PreferenceManager.getDefaultSharedPreferences(context);
    this.preferenceName = preferenceName;
    this.recentlyUsed   = getPersistedCache();
  }

  private LinkedHashSet<String> getPersistedCache() {
    String serialized = prefs.getString(preferenceName, "[]");
    try {
      CollectionType collectionType = TypeFactory.defaultInstance()
                                                 .constructCollectionType(LinkedHashSet.class, String.class);
      return JsonUtils.getMapper().readValue(serialized, collectionType);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new LinkedHashSet<>();
    }
  }

  public List<String> getEmoji() {
    List<String> emoji = new ArrayList<>(recentlyUsed);
    Collections.reverse(emoji);
    return emoji;
  }

  @MainThread
  public void onCodePointSelected(String emoji) {
    recentlyUsed.remove(emoji);
    recentlyUsed.add(emoji);

    if (recentlyUsed.size() > EMOJI_LRU_SIZE) {
      Iterator<String> iterator = recentlyUsed.iterator();
      iterator.next();
      iterator.remove();
    }

    final LinkedHashSet<String> latestRecentlyUsed = new LinkedHashSet<>(recentlyUsed);
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        String serialized = JsonUtils.toJson(latestRecentlyUsed);
        prefs.edit()
             .putString(preferenceName, serialized)
             .apply();
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    });
  }
}
