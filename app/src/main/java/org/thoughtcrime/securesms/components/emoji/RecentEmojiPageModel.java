package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import network.loki.messenger.R;

public class RecentEmojiPageModel implements EmojiPageModel {
  private static final String TAG                  = RecentEmojiPageModel.class.getSimpleName();
  private static final String EMOJI_LRU_PREFERENCE = "pref_recent_emoji2";
  private static final int    EMOJI_LRU_SIZE       = 50;
  public static final  String KEY                  = "Recents";
  public static final List<String> DEFAULT_REACTIONS_LIST =
          Arrays.asList("\ud83d\ude02", "\ud83e\udd70", "\ud83d\ude22", "\ud83d\ude21", "\ud83d\ude2e", "\ud83d\ude08");

  private final SharedPreferences     prefs;
  private final LinkedHashSet<String> recentlyUsed;

  public RecentEmojiPageModel(Context context) {
    this.prefs        = PreferenceManager.getDefaultSharedPreferences(context);
    this.recentlyUsed = getPersistedCache();
  }

  private LinkedHashSet<String> getPersistedCache() {
    String serialized = prefs.getString(EMOJI_LRU_PREFERENCE, "[]");
    try {
      CollectionType collectionType = TypeFactory.defaultInstance()
                                                 .constructCollectionType(LinkedHashSet.class, String.class);
      return JsonUtil.getMapper().readValue(serialized, collectionType);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new LinkedHashSet<>();
    }
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override public int getIconAttr() {
    return R.attr.emoji_category_recent;
  }

  @Override public List<String> getEmoji() {
    List<String> recent = new ArrayList<>(recentlyUsed);
    List<String> out = new ArrayList<>(DEFAULT_REACTIONS_LIST.size());

    for (int i = 0; i < DEFAULT_REACTIONS_LIST.size(); i++) {
      if (recent.size() > i) {
        out.add(recent.get(i));
      } else {
        out.add(DEFAULT_REACTIONS_LIST.get(i));
      }
    }

    return out;
  }

  @Override public List<Emoji> getDisplayEmoji() {
    return Stream.of(getEmoji()).map(Emoji::new).toList();
  }

  @Override public boolean hasSpriteMap() {
    return false;
  }

  @Nullable
  @Override
  public Uri getSpriteUri() {
    return null;
  }

  @Override public boolean isDynamic() {
    return true;
  }

  public void onCodePointSelected(String emoji) {
    recentlyUsed.remove(emoji);
    recentlyUsed.add(emoji);

    if (recentlyUsed.size() > EMOJI_LRU_SIZE) {
      Iterator<String> iterator = recentlyUsed.iterator();
      iterator.next();
      iterator.remove();
    }

    final LinkedHashSet<String> latestRecentlyUsed = new LinkedHashSet<>(recentlyUsed);
    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(Void... params) {
        try {
          String serialized = JsonUtil.toJsonThrows(latestRecentlyUsed);
          prefs.edit()
               .putString(EMOJI_LRU_PREFERENCE, serialized)
               .apply();
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private String[] toReversePrimitiveArray(@NonNull LinkedHashSet<String> emojiSet) {
    String[] emojis = new String[emojiSet.size()];
    int i = emojiSet.size() - 1;
    for (String emoji : emojiSet) {
      emojis[i--] = emoji;
    }
    return emojis;
  }
}
