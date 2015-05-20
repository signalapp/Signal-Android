package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;

public class RecentEmojiPageModel implements EmojiPageModel {
  private static final String TAG                  = RecentEmojiPageModel.class.getSimpleName();
  private static final String EMOJI_LRU_PREFERENCE = "pref_recent_emoji";
  private static final int    EMOJI_LRU_SIZE       = 50;

  private final SharedPreferences      prefs;
  private final LinkedHashSet<Integer> recentlyUsed;

  public RecentEmojiPageModel(Context context) {
    this.prefs        = PreferenceManager.getDefaultSharedPreferences(context);
    this.recentlyUsed = getPersistedCache();
  }

  private LinkedHashSet<Integer> getPersistedCache() {
    String serialized = prefs.getString(EMOJI_LRU_PREFERENCE, "[]");
    LinkedHashSet<String> recentlyUsedStrings;
    try {
      CollectionType collectionType = TypeFactory.defaultInstance()
                                                 .constructCollectionType(LinkedHashSet.class, String.class);
      recentlyUsedStrings = JsonUtils.getMapper().readValue(serialized, collectionType);
      return fromHexString(recentlyUsedStrings);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new LinkedHashSet<>();
    }
  }

  @Override public int getIconRes() {
    return R.drawable.emoji_category_recent;
  }

  @Override public int[] getCodePoints() {
    return toReversePrimitiveArray(recentlyUsed);
  }

  @Override public boolean isDynamic() {
    return true;
  }

  public void onCodePointSelected(int codePoint) {
    Log.w(TAG, "onCodePointSelected(" + codePoint + ")");
    recentlyUsed.remove(codePoint);
    recentlyUsed.add(codePoint);

    if (recentlyUsed.size() > EMOJI_LRU_SIZE) {
      Iterator<Integer> iterator = recentlyUsed.iterator();
      iterator.next();
      iterator.remove();
    }

    final LinkedHashSet<Integer> latestRecentlyUsed = new LinkedHashSet<>(recentlyUsed);
    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(Void... params) {
        try {
          String serialized = JsonUtils.toJson(toHexString(latestRecentlyUsed));
          prefs.edit()
               .putString(EMOJI_LRU_PREFERENCE, serialized)
               .apply();
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        return null;
      }
    }.execute();
  }

  private LinkedHashSet<Integer> fromHexString(@Nullable LinkedHashSet<String> stringSet) {
    final LinkedHashSet<Integer> integerSet = new LinkedHashSet<>(stringSet != null ? stringSet.size() : 0);
    if (stringSet != null) {
      for (String hexString : stringSet) {
        integerSet.add(Integer.valueOf(hexString, 16));
      }
    }
    return integerSet;
  }

  private LinkedHashSet<String> toHexString(@NonNull LinkedHashSet<Integer> integerSet) {
    final LinkedHashSet<String> stringSet = new LinkedHashSet<>(integerSet.size());
    for (Integer integer : integerSet) {
      stringSet.add(Integer.toHexString(integer));
    }
    return stringSet;
  }

  private int[] toReversePrimitiveArray(@NonNull LinkedHashSet<Integer> integerSet) {
    int[] ints = new int[integerSet.size()];
    int i = integerSet.size() - 1;
    for (Integer integer : integerSet) {
      ints[i--] = integer;
    }
    return ints;
  }
}
