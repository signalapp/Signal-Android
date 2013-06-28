package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
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
  public  static final double  EMOJI_SMALL = 0.7;

  private final Context     context;
  private final String[]    emojiAssets;
  private final Set<String> emojiAssetsSet;

  private Emoji(Context context) {
    this.context        = context.getApplicationContext();
    this.emojiAssets    = initializeEmojiAssets();
    this.emojiAssetsSet = new HashSet<String>();

    Collections.addAll(this.emojiAssetsSet, emojiAssets);
  }

  public int getEmojiAssetCount() {
    return emojiAssets.length;
  }

  public String getEmojiUnicode(int position) {
    String  hexString    = emojiAssets[position].split("\\.")[0];
    Integer unicodePoint = Integer.parseInt(hexString, 16);
    return new String(Character.toChars(unicodePoint));
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
          drawable.setBounds(0, 0, (int)(drawable.getIntrinsicWidth()*size),
                             (int)(drawable.getIntrinsicHeight()*size));


          ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
          text.setSpan(imageSpan, matches.start(), matches.end(),
                       Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        }
      }
    }

    return text;
  }

  private Drawable getEmojiDrawable(String assetName) {
    try {
      return Drawable.createFromStream(context.getAssets().open("emoji" + File.separator + assetName), null);
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

}
