package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.reflect.TypeToken;

import org.thoughtcrime.securesms.R;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Emoji {

  private static Emoji instance = null;

  public synchronized static Emoji getInstance(Context context) {
    if (instance == null) {
      instance = new Emoji(context);
    }

    return instance;
  }

  private static final Pattern EMOJI_RANGE = Pattern.compile("[\ud83d\ude01-\ud83d\ude4f]");
  public  static final double  EMOJI_LARGE = 1;
  public  static final double  EMOJI_SMALL = 0.75;
  public  static final int     EMOJI_LARGE_SIZE = 22;

  private final Context               context;
  private final String[]              emojiAssets;
  private final Set<String>           emojiAssetsSet;
  private final BitmapFactory.Options bitmapOptions;
  private final int                   bigDrawSize;

  private Emoji(Context context) {
    this.context        = context.getApplicationContext();
    this.emojiAssets    = initializeEmojiAssets();
    this.emojiAssetsSet = new HashSet<String>();
    this.bitmapOptions  = initializeBitmapOptions();

    this.bigDrawSize = scale(EMOJI_LARGE_SIZE);

    Collections.addAll(this.emojiAssetsSet, emojiAssets);
  }

  private int scale(float value) {
    return (int)(context.getResources().getDisplayMetrics().density * value);
  }

  public int getEmojiAssetCount() {
    return emojiAssets.length;
  }

  public String getEmojiUnicode(int position) {
    return getEmojiUnicodeFromAssetName(emojiAssets[position]);
  }

  public String getRecentEmojiUnicode(int position) {
    return getEmojiUnicodeFromAssetName(EmojiLRU.getRecentlyUsed(context).get(position));
  }

  public Drawable getEmojiDrawable(int position) {
    return getEmojiDrawable(emojiAssets[position]);
  }

  public SpannableString emojify(String text) {
    return emojify(new SpannableString(text), EMOJI_LARGE);
  }

  public SpannableString emojify(SpannableString text, double size) {
    if (text.toString().contains("\ud83d")) {

      Matcher matches = EMOJI_RANGE.matcher(text);

      while (matches.find()) {
        String resource = Integer.toHexString(matches.group().codePointAt(0)) + ".png";

        if (emojiAssetsSet.contains(resource)) {
          Drawable drawable = getEmojiDrawable(resource);
          drawable.setBounds(0, 0, (int)(bigDrawSize*size),
                             (int)(bigDrawSize*size));

          ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
          text.setSpan(imageSpan, matches.start(), matches.end(),
                       Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        }
      }
    }

    return text;
  }

  public void setRecentlyUsed(int position) {
    String assetName = emojiAssets[position];
    EmojiLRU.putRecentlyUsed(context, assetName);
  }

  public int getRecentlyUsedAssetCount() {
    return EmojiLRU.getRecentlyUsed(context).size();
  }

  public Drawable getRecentlyUsed(int position) {
    return getEmojiDrawable(EmojiLRU.getRecentlyUsed(context).get(position));
  }

  private String getEmojiUnicodeFromAssetName(String assetName) {
    String hexString     = assetName.split("\\.")[0];
    Integer unicodePoint = Integer.parseInt(hexString, 16);
    return new String(Character.toChars(unicodePoint));
  }

  private Drawable getEmojiDrawable(String assetName) {
    try {
      Bitmap bitmap = BitmapFactory.decodeStream(context.getAssets().open("emoji" + File.separator + assetName),
                                                 null, bitmapOptions);

      bitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true);

      return  new BitmapDrawable(context.getResources(), bitmap);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private String[] initializeEmojiAssets() {
    try {
      return context.getAssets().list("emoji");
    } catch (IOException e) {
      Log.w("Emoji", e);
      return new String[0];
    }
  }

  private BitmapFactory.Options initializeBitmapOptions() {
    BitmapFactory.Options options = new BitmapFactory.Options();

    options.inScaled           = true;
    options.inDensity          = DisplayMetrics.DENSITY_MEDIUM;
    options.inTargetDensity    = context.getResources().getDisplayMetrics().densityDpi;
    options.inSampleSize       = 1;
    options.inJustDecodeBounds = false;

    return options;
  }

  private static class EmojiLRU {

    private static final String EMOJI_LRU_PREFERENCE = "pref_popular_emoji";
    private static final int    EMOJI_LRU_SIZE       = 10;

    public static List<String> getRecentlyUsed(Context context) {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      String            serialized  = preferences.getString(EMOJI_LRU_PREFERENCE, "[]");
      Type              type        = new TypeToken<Collection<String>>(){}.getType();

      return new Gson().fromJson(serialized, type);
    }

    public static void putRecentlyUsed(Context context, String asset) {
      LinkedHashSet<String> recentlyUsed = new LinkedHashSet<String>(getRecentlyUsed(context));
      recentlyUsed.add(asset);

      if (recentlyUsed.size() > 10) {
        Iterator<String> iterator = recentlyUsed.iterator();
        iterator.next();
        iterator.remove();
      }

      String serialized = new Gson().toJson(recentlyUsed);
      PreferenceManager.getDefaultSharedPreferences(context)
                       .edit()
                       .putString(EMOJI_LRU_PREFERENCE, serialized)
                       .apply();
    }
  }
}
