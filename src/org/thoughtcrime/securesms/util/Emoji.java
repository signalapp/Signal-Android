package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Emoji {

  public static final HashMap<String, String> EMOJI_ASSET_CODE_MAP = new HashMap<String, String>();
  public static final HashMap<Pattern, String> EMOJI_CODE_ASSET_MAP = new HashMap<Pattern, String>();

  public static final ArrayList<String> EMOJI_ASSETS = new ArrayList<String>() {{
    add("1f601");
    add("1f602");
    add("1f603");
    add("1f604");
    add("1f605");
    add("1f606");
    add("1f609");
    add("1f60a");
    add("1f60b");
    add("1f60c");
    add("1f60d");
    add("1f60f");
    add("1f612");
    add("1f613");
    add("1f614");
    add("1f616");
    add("1f618");
    add("1f61a");
    add("1f61c");
    add("1f61d");
    add("1f61e");
    add("1f620");
    add("1f621");
    add("1f622");
    add("1f623");
    add("1f624");
    add("1f625");
    add("1f628");
    add("1f629");
    add("1f62a");
    add("1f62b");
    add("1f62d");
    add("1f630");
    add("1f631");
    add("1f632");
    add("1f633");
    add("1f635");
    add("1f637");

    add("1f638");
    add("1f639");
    add("1f63a");
    add("1f63b");
    add("1f63c");
    add("1f63d");
    add("1f63e");
    add("1f63f");
    add("1f640");
    add("1f645");
    add("1f646");
    add("1f647");
    add("1f648");
    add("1f649");
    add("1f64a");
    add("1f64b");
    add("1f64c");
    add("1f64d");
    add("1f64e");
    add("1f64f");
  }};

  public static ArrayList<String> EMOJI_CODES = new ArrayList<String>() {{
    add("\ud83d\ude01");
    add("\ud83d\ude02");
    add("\ud83d\ude03");
    add("\ud83d\ude04");
    add("\ud83d\ude05");
    add("\ud83d\ude06");
    add("\ud83d\ude09");
    add("\ud83d\ude0a");
    add("\ud83d\ude0b");
    add("\ud83d\ude0c");
    add("\ud83d\ude0d");
    add("\ud83d\ude0f");
    add("\ud83d\ude12");
    add("\ud83d\ude13");
    add("\ud83d\ude14");
    add("\ud83d\ude16");
    add("\ud83d\ude18");
    add("\ud83d\ude1a");
    add("\ud83d\ude1c");
    add("\ud83d\ude1d");
    add("\ud83d\ude1e");
    add("\ud83d\ude20");
    add("\ud83d\ude21");
    add("\ud83d\ude22");
    add("\ud83d\ude23");
    add("\ud83d\ude24");
    add("\ud83d\ude25");
    add("\ud83d\ude28");
    add("\ud83d\ude29");
    add("\ud83d\ude2a");
    add("\ud83d\ude2b");
    add("\ud83d\ude2d");
    add("\ud83d\ude30");
    add("\ud83d\ude31");
    add("\ud83d\ude32");
    add("\ud83d\ude33");
    add("\ud83d\ude35");
    add("\ud83d\ude37");

    add("\ud83d\ude38");
    add("\ud83d\ude39");
    add("\ud83d\ude3a");
    add("\ud83d\ude3b");
    add("\ud83d\ude3c");
    add("\ud83d\ude3d");
    add("\ud83d\ude3e");
    add("\ud83d\ude3f");
    add("\ud83d\ude40");
    add("\ud83d\ude45");
    add("\ud83d\ude46");
    add("\ud83d\ude47");
    add("\ud83d\ude48");
    add("\ud83d\ude49");
    add("\ud83d\ude4a");
    add("\ud83d\ude4b");
    add("\ud83d\ude4c");
    add("\ud83d\ude4d");
    add("\ud83d\ude4e");
    add("\ud83d\ude4f");
  }};

  static {
    for (int i=0;i<EMOJI_ASSETS.size();i++) {
      EMOJI_ASSET_CODE_MAP.put(EMOJI_ASSETS.get(i), EMOJI_CODES.get(i));
      EMOJI_CODE_ASSET_MAP.put(Pattern.compile(EMOJI_CODES.get(i)), EMOJI_ASSETS.get(i));
    }
  }

  public static Spannable emojify(Context context, String text) {
    try {
      Spannable spannable = new SpannableString(text);

      for (Map.Entry<Pattern, String> entry : EMOJI_CODE_ASSET_MAP.entrySet()) {
        Matcher matcher = entry.getKey().matcher(spannable);

        while (matcher.find()) {
          Drawable asset = Drawable.createFromStream(context.getAssets().open(entry.getValue() + ".png"), null);
          asset.setBounds(0, 0, asset.getIntrinsicWidth(), asset.getIntrinsicHeight());

          ImageSpan imageSpan = new ImageSpan(asset, ImageSpan.ALIGN_BASELINE);
          Log.w("Emoji", "Replacing text with: " + imageSpan);
          spannable.setSpan(imageSpan,matcher.start(), matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }

      return spannable;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
