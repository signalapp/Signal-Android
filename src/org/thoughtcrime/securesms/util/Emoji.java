package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;

import com.fasterxml.jackson.databind.type.TypeFactory;

import org.thoughtcrime.securesms.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Emoji {

  private static final String TAG = Emoji.class.getSimpleName();

  private static ExecutorService executor = Util.newSingleThreadedLifoExecutor();

  public static final int[][] PAGES = {
      {
          0x263a,  0x1f60a, 0x1f600, 0x1f601, 0x1f602, 0x1f603, 0x1f604, 0x1f605,
          0x1f606, 0x1f607, 0x1f608, 0x1f609, 0x1f62f, 0x1f610, 0x1f611, 0x1f615,
          0x1f620, 0x1f62c, 0x1f621, 0x1f622, 0x1f634, 0x1f62e, 0x1f623, 0x1f624,
          0x1f625, 0x1f626, 0x1f627, 0x1f628, 0x1f629, 0x1f630, 0x1f61f, 0x1f631,
          0x1f632, 0x1f633, 0x1f635, 0x1f636, 0x1f637, 0x1f61e, 0x1f612, 0x1f60d,
          0x1f61b, 0x1f61c, 0x1f61d, 0x1f60b, 0x1f617, 0x1f619, 0x1f618, 0x1f61a,
          0x1f60e, 0x1f62d, 0x1f60c, 0x1f616, 0x1f614, 0x1f62a, 0x1f60f, 0x1f613,
          0x1f62b, 0x1f64b, 0x1f64c, 0x1f64d, 0x1f645, 0x1f646, 0x1f647, 0x1f64e,
          0x1f64f, 0x1f63a, 0x1f63c, 0x1f638, 0x1f639, 0x1f63b, 0x1f63d, 0x1f63f,
          0x1f63e, 0x1f640, 0x1f648, 0x1f649, 0x1f64a, 0x1f4a9, 0x1f476, 0x1f466,
          0x1f467, 0x1f468, 0x1f469, 0x1f474, 0x1f475, 0x1f48f, 0x1f491, 0x1f46a,
          0x1f46b, 0x1f46c, 0x1f46d, 0x1f464, 0x1f465, 0x1f46e, 0x1f477, 0x1f481,
          0x1f482, 0x1f46f, 0x1f470, 0x1f478, 0x1f385, 0x1f47c, 0x1f471, 0x1f472,
          0x1f473, 0x1f483, 0x1f486, 0x1f487, 0x1f485, 0x1f47b, 0x1f479, 0x1f47a,
          0x1f47d, 0x1f47e, 0x1f47f, 0x1f480, 0x1f4aa, 0x1f440, 0x1f442, 0x1f443,
          0x1f463, 0x1f444, 0x1f445, 0x1f48b, 0x2764,  0x1f499, 0x1f49a, 0x1f49b,
          0x1f49c, 0x1f493, 0x1f494, 0x1f495, 0x1f496, 0x1f497, 0x1f498, 0x1f49d,
          0x1f49e, 0x1f49f, 0x1f44d, 0x1f44e, 0x1f44c, 0x270a,  0x270c,  0x270b,
          0x1f44a, 0x261d,  0x1f446, 0x1f447, 0x1f448, 0x1f449, 0x1f44b, 0x1f44f,
          0x1f450
      },
      {
          0x1f530, 0x1f484, 0x1f45e, 0x1f45f, 0x1f451, 0x1f452, 0x1f3a9, 0x1f393,
          0x1f453, 0x231a,  0x1f454, 0x1f455, 0x1f456, 0x1f457, 0x1f458, 0x1f459,
          0x1f460, 0x1f461, 0x1f462, 0x1f45a, 0x1f45c, 0x1f4bc, 0x1f392, 0x1f45d,
          0x1f45b, 0x1f4b0, 0x1f4b3, 0x1f4b2, 0x1f4b5, 0x1f4b4, 0x1f4b6, 0x1f4b7,
          0x1f4b8, 0x1f4b1, 0x1f4b9, 0x1f52b, 0x1f52a, 0x1f4a3, 0x1f489, 0x1f48a,
          0x1f6ac, 0x1f514, 0x1f515, 0x1f6aa, 0x1f52c, 0x1f52d, 0x1f52e, 0x1f526,
          0x1f50b, 0x1f50c, 0x1f4dc, 0x1f4d7, 0x1f4d8, 0x1f4d9, 0x1f4da, 0x1f4d4,
          0x1f4d2, 0x1f4d1, 0x1f4d3, 0x1f4d5, 0x1f4d6, 0x1f4f0, 0x1f4db, 0x1f383,
          0x1f384, 0x1f380, 0x1f381, 0x1f382, 0x1f388, 0x1f386, 0x1f387, 0x1f389,
          0x1f38a, 0x1f38d, 0x1f38f, 0x1f38c, 0x1f390, 0x1f38b, 0x1f38e, 0x1f4f1,
          0x1f4f2, 0x1f4df, 0x260e,  0x1f4de, 0x1f4e0, 0x1f4e6, 0x2709,  0x1f4e8,
          0x1f4e9, 0x1f4ea, 0x1f4eb, 0x1f4ed, 0x1f4ec, 0x1f4ee, 0x1f4e4, 0x1f4e5,
          0x1f4ef, 0x1f4e2, 0x1f4e3, 0x1f4e1, 0x1f4ac, 0x1f4ad, 0x2712,  0x270f,
          0x1f4dd, 0x1f4cf, 0x1f4d0, 0x1f4cd, 0x1f4cc, 0x1f4ce, 0x2702,  0x1f4ba,
          0x1f4bb, 0x1f4bd, 0x1f4be, 0x1f4bf, 0x1f4c6, 0x1f4c5, 0x1f4c7, 0x1f4cb,
          0x1f4c1, 0x1f4c2, 0x1f4c3, 0x1f4c4, 0x1f4ca, 0x1f4c8, 0x1f4c9, 0x26fa,
          0x1f3a1, 0x1f3a2, 0x1f3a0, 0x1f3aa, 0x1f3a8, 0x1f3ac, 0x1f3a5, 0x1f4f7,
          0x1f4f9, 0x1f3a6, 0x1f3ad, 0x1f3ab, 0x1f3ae, 0x1f3b2, 0x1f3b0, 0x1f0cf,
          0x1f3b4, 0x1f004, 0x1f3af, 0x1f4fa, 0x1f4fb, 0x1f4c0, 0x1f4fc, 0x1f3a7,
          0x1f3a4, 0x1f3b5, 0x1f3b6, 0x1f3bc, 0x1f3bb, 0x1f3b9, 0x1f3b7, 0x1f3ba,
          0x1f3b8, 0x303d
      },
      {
          0x1f415, 0x1f436, 0x1f429, 0x1f408, 0x1f431, 0x1f400, 0x1f401, 0x1f42d,
          0x1f439, 0x1f422, 0x1f407, 0x1f430, 0x1f413, 0x1f414, 0x1f423, 0x1f424,
          0x1f425, 0x1f426, 0x1f40f, 0x1f411, 0x1f410, 0x1f43a, 0x1f403, 0x1f402,
          0x1f404, 0x1f42e, 0x1f434, 0x1f417, 0x1f416, 0x1f437, 0x1f43d, 0x1f438,
          0x1f40d, 0x1f43c, 0x1f427, 0x1f418, 0x1f428, 0x1f412, 0x1f435, 0x1f406,
          0x1f42f, 0x1f43b, 0x1f42b, 0x1f42a, 0x1f40a, 0x1f433, 0x1f40b, 0x1f41f,
          0x1f420, 0x1f421, 0x1f419, 0x1f41a, 0x1f42c, 0x1f40c, 0x1f41b, 0x1f41c,
          0x1f41d, 0x1f41e, 0x1f432, 0x1f409, 0x1f43e, 0x1f378, 0x1f37a, 0x1f37b,
          0x1f377, 0x1f379, 0x1f376, 0x2615,  0x1f375, 0x1f37c, 0x1f374, 0x1f368,
          0x1f367, 0x1f366, 0x1f369, 0x1f370, 0x1f36a, 0x1f36b, 0x1f36c, 0x1f36d,
          0x1f36e, 0x1f36f, 0x1f373, 0x1f354, 0x1f35f, 0x1f35d, 0x1f355, 0x1f356,
          0x1f357, 0x1f364, 0x1f363, 0x1f371, 0x1f35e, 0x1f35c, 0x1f359, 0x1f35a,
          0x1f35b, 0x1f372, 0x1f365, 0x1f362, 0x1f361, 0x1f358, 0x1f360, 0x1f34c,
          0x1f34e, 0x1f34f, 0x1f34a, 0x1f34b, 0x1f344, 0x1f345, 0x1f346, 0x1f347,
          0x1f348, 0x1f349, 0x1f350, 0x1f351, 0x1f352, 0x1f353, 0x1f34d, 0x1f330,
          0x1f331, 0x1f332, 0x1f333, 0x1f334, 0x1f335, 0x1f337, 0x1f338, 0x1f339,
          0x1f340, 0x1f341, 0x1f342, 0x1f343, 0x1f33a, 0x1f33b, 0x1f33c, 0x1f33d,
          0x1f33e, 0x1f33f, 0x2600,  0x1f308, 0x26c5,  0x2601,  0x1f301, 0x1f302,
          0x2614,  0x1f4a7, 0x26a1,  0x1f300, 0x2744,  0x26c4,  0x1f319, 0x1f31e,
          0x1f31d, 0x1f31a, 0x1f31b, 0x1f31c, 0x1f311, 0x1f312, 0x1f313, 0x1f314,
          0x1f315, 0x1f316, 0x1f317, 0x1f318, 0x1f391, 0x1f304, 0x1f305, 0x1f307,
          0x1f306, 0x1f303, 0x1f30c, 0x1f309, 0x1f30a, 0x1f30b, 0x1f30e, 0x1f30f,
          0x1f30d, 0x1f310
      },
      {
          0x1f3e0, 0x1f3e1, 0x1f3e2, 0x1f3e3, 0x1f3e4, 0x1f3e5, 0x1f3e6, 0x1f3e7,
          0x1f3e8, 0x1f3e9, 0x1f3ea, 0x1f3eb, 0x26ea,  0x26f2,  0x1f3ec, 0x1f3ef,
          0x1f3f0, 0x1f3ed, 0x1f5fb, 0x1f5fc, 0x1f5fd, 0x1f5fe, 0x1f5ff, 0x2693,
          0x1f3ee, 0x1f488, 0x1f527, 0x1f528, 0x1f529, 0x1f6bf, 0x1f6c1, 0x1f6c0,
          0x1f6bd, 0x1f6be, 0x1f3bd, 0x1f3a3, 0x1f3b1, 0x1f3b3, 0x26be,  0x26f3,
          0x1f3be, 0x26bd,  0x1f3bf, 0x1f3c0, 0x1f3c1, 0x1f3c2, 0x1f3c3, 0x1f3c4,
          0x1f3c6, 0x1f3c7, 0x1f40e, 0x1f3c8, 0x1f3c9, 0x1f3ca, 0x1f682, 0x1f683,
          0x1f684, 0x1f685, 0x1f686, 0x1f687, 0x24c2,  0x1f688, 0x1f68a, 0x1f68b,
          0x1f68c, 0x1f68d, 0x1f68e, 0x1f68f, 0x1f690, 0x1f691, 0x1f692, 0x1f693,
          0x1f694, 0x1f695, 0x1f696, 0x1f697, 0x1f698, 0x1f699, 0x1f69a, 0x1f69b,
          0x1f69c, 0x1f69d, 0x1f69e, 0x1f69f, 0x1f6a0, 0x1f6a1, 0x1f6a2, 0x1f6a3,
          0x1f681, 0x2708,  0x1f6c2, 0x1f6c3, 0x1f6c4, 0x1f6c5, 0x26f5,  0x1f6b2,
          0x1f6b3, 0x1f6b4, 0x1f6b5, 0x1f6b7, 0x1f6b8, 0x1f689, 0x1f680, 0x1f6a4,
          0x1f6b6, 0x26fd,  0x1f17f, 0x1f6a5, 0x1f6a6, 0x1f6a7, 0x1f6a8, 0x2668,
          0x1f48c, 0x1f48d, 0x1f48e, 0x1f490, 0x1f492, 0xfe4e5, 0xfe4e6, 0xfe4e7,
          0xfe4e8, 0xfe4e9, 0xfe4ea, 0xfe4eb, 0xfe4ec, 0xfe4ed, 0xfe4ee
      },
      {
          0x1f51d, 0x1f519, 0x1f51b, 0x1f51c, 0x1f51a, 0x23f3,  0x231b,  0x23f0,
          0x2648,  0x2649,  0x264a,  0x264b,  0x264c,  0x264d,  0x264e,  0x264f,
          0x2650,  0x2651,  0x2652,  0x2653,  0x26ce,  0x1f531, 0x1f52f, 0x1f6bb,
          0x1f6ae, 0x1f6af, 0x1f6b0, 0x1f6b1, 0x1f170, 0x1f171, 0x1f18e, 0x1f17e,
          0x1f4ae, 0x1f4af, 0x1f520, 0x1f521, 0x1f522, 0x1f523, 0x1f524, 0x27bf,
          0x1f4f6, 0x1f4f3, 0x1f4f4, 0x1f4f5, 0x1f6b9, 0x1f6ba, 0x1f6bc, 0x267f,
          0x267b,  0x1f6ad, 0x1f6a9, 0x26a0,  0x1f201, 0x1f51e, 0x26d4,  0x1f192,
          0x1f197, 0x1f195, 0x1f198, 0x1f199, 0x1f193, 0x1f196, 0x1f19a, 0x1f232,
          0x1f233, 0x1f234, 0x1f235, 0x1f236, 0x1f237, 0x1f238, 0x1f239, 0x1f202,
          0x1f23a, 0x1f250, 0x1f251, 0x3299,  0x00ae,  0x00a9,  0x2122,  0x1f21a,
          0x1f22f, 0x3297,  0x2b55,  0x274c,  0x274e,  0x2139,  0x1f6ab, 0x2705,
          0x2714,  0x1f517, 0x2734,  0x2733,  0x2795,  0x2796,  0x2716,  0x2797,
          0x1f4a0, 0x1f4a1, 0x1f4a4, 0x1f4a2, 0x1f525, 0x1f4a5, 0x1f4a8, 0x1f4a6,
          0x1f4ab, 0x1f55b, 0x1f567, 0x1f550, 0x1f55c, 0x1f551, 0x1f55d, 0x1f552,
          0x1f55e, 0x1f553, 0x1f55f, 0x1f554, 0x1f560, 0x1f555, 0x1f561, 0x1f556,
          0x1f562, 0x1f557, 0x1f563, 0x1f558, 0x1f564, 0x1f559, 0x1f565, 0x1f55a,
          0x1f566, 0x2195,  0x2b06,  0x2197,  0x27a1,  0x2198,  0x2b07,  0x2199,
          0x2b05,  0x2196,  0x2194,  0x2934,  0x2935,  0x23ea,  0x23eb,  0x23ec,
          0x23e9,  0x25c0,  0x25b6,  0x1f53d, 0x1f53c, 0x2747,  0x2728,  0x1f534,
          0x1f535, 0x26aa,  0x26ab,  0x1f533, 0x1f532, 0x2b50,  0x1f31f, 0x1f320,
          0x25ab,  0x25aa,  0x25fd,  0x25fe,  0x25fb,  0x25fc,  0x2b1c,  0x2b1b,
          0x1f538, 0x1f539, 0x1f536, 0x1f537, 0x1f53a, 0x1f53b, 0x1f51f, /*0x20e3,*/
          0x2754,  0x2753,  0x2755,  0x2757,  0x203c,  0x2049,  0x3030,  0x27b0,
          0x2660,  0x2665,  0x2663,  0x2666,  0x1f194, 0x1f511, 0x21a9,  0x1f191,
          0x1f50d, 0x1f512, 0x1f513, 0x21aa,  0x1f510, 0x2611,  0x1f518, 0x1f50e,
          0x1f516, 0x1f50f, 0x1f503, 0x1f500, 0x1f501, 0x1f502, 0x1f504, 0x1f4e7,
          0x1f505, 0x1f506, 0x1f507, 0x1f508, 0x1f509, 0x1f50a
      }
  };

  private static final SparseArray<DrawInfo> offsets;

  static {
    offsets = new SparseArray<DrawInfo>();
    for (int i = 0; i < PAGES.length; i++) {
      for (int j = 0; j < PAGES[i].length; j++) {
        offsets.put(PAGES[i][j], new DrawInfo(i, j));
      }
    }
  }

  private static Bitmap[] bitmaps = new Bitmap[PAGES.length];

  private static Emoji instance = null;

  public synchronized static Emoji getInstance(Context context) {
    if (instance == null) {
      instance = new Emoji(context);
    }

    return instance;
  }

  @SuppressWarnings("MalformedRegex")
  //                                                            0x20a0-0x32ff          0x1f00-0x1fff              0xfe4e5-0xfe4ee
  //                                                           |==== misc ====||======== emoticons ========||========= flags ==========|
  private static final Pattern EMOJI_RANGE = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]");

  public static final double EMOJI_HUGE       = 1.00;
  public static final double EMOJI_LARGE      = 0.75;
  public static final double EMOJI_SMALL      = 0.50;
  public static final int    EMOJI_RAW_SIZE   =  128;
  public static final int    EMOJI_PER_ROW    =   16;

  private final Context context;
  private final int     bigDrawSize;

  private Emoji(Context context) {
    this.context = context.getApplicationContext();
    this.bigDrawSize = context.getResources().getDimensionPixelSize(R.dimen.emoji_drawer_size);
  }

  private void preloadPage(final int page, final PageLoadedListener pageLoadListener) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          loadPage(page);
          if (pageLoadListener != null) pageLoadListener.onPageLoaded();
        } catch (IOException ioe) {
          Log.w("Emoji", ioe);
        }
      }
    });
  }

  private void loadPage(int page) throws IOException {
    if (page < 0 || page >= PAGES.length) {
      throw new IndexOutOfBoundsException("can't load page that doesn't exist");
    }

    if (bitmaps[page] != null) return;

    try {
      final String file = "emoji_" + page + "_wrapped.png";
      final InputStream measureStream = context.getAssets().open(file);
      final InputStream bitmapStream = context.getAssets().open(file);

      bitmaps[page] = BitmapUtil.createScaledBitmap(measureStream, bitmapStream, (float) bigDrawSize / (float) EMOJI_RAW_SIZE);
    } catch (IOException ioe) {
      Log.w("Emoji", ioe);
      throw ioe;
    } catch (BitmapDecodingException bde) {
      Log.w("Emoji", bde);
      throw new AssertionError("emoji sprite asset is corrupted or android decoding is broken");
    }
  }

  public SpannableString emojify(String text, PageLoadedListener pageLoadedListener) {
    return emojify(new SpannableString(text), pageLoadedListener);
  }

  public SpannableString emojify(SpannableString text, PageLoadedListener pageLoadedListener) {
    return emojify(text, EMOJI_LARGE, pageLoadedListener);
  }

  public SpannableString emojify(SpannableString text, double size, PageLoadedListener pageLoadedListener) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) return text;

    Matcher matches = EMOJI_RANGE.matcher(text);

    while (matches.find()) {
      String resource = Integer.toHexString(matches.group().codePointAt(0));

      Drawable drawable = getEmojiDrawable(resource, size, pageLoadedListener);
      if (drawable != null) {
        ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
        text.setSpan(imageSpan, matches.start(), matches.end(),
                     Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return text;
  }

  public Pair<Integer, Drawable> getRecentlyUsed(int position, double size, PageLoadedListener pageLoadedListener) {
    String[] recentlyUsed = EmojiLRU.getRecentlyUsed(context);
    String   code         = recentlyUsed[recentlyUsed.length - 1 - position];
    return new Pair<Integer, Drawable>(Integer.parseInt(code, 16), getEmojiDrawable(code, size, pageLoadedListener));
  }

  public void setRecentlyUsed(String emojiCode) {
    EmojiLRU.putRecentlyUsed(context, emojiCode);
  }

  public int getRecentlyUsedAssetCount() {
    return EmojiLRU.getRecentlyUsedCount(context);
  }

  public Drawable getEmojiDrawable(String emojiCode, double size, PageLoadedListener pageLoadedListener) {
    return getEmojiDrawable(offsets.get(Integer.parseInt(emojiCode, 16)), size, pageLoadedListener);
  }

  public Drawable getEmojiDrawable(DrawInfo drawInfo, double size, PageLoadedListener pageLoadedListener) {
    if (drawInfo == null) {
      return null;
    }
    final Drawable drawable = new EmojiDrawable(drawInfo, bigDrawSize);
    drawable.setBounds(0, 0, (int) ((double) bigDrawSize * size), (int) ((double) bigDrawSize * size));
    if (bitmaps[drawInfo.page] == null) {
      preloadPage(drawInfo.page, pageLoadedListener);
    }
    return drawable;
  }

  private static class EmojiLRU {
    private static       SharedPreferences     prefs                = null;
    private static       LinkedHashSet<String> recentlyUsed         = null;
    private static final String                EMOJI_LRU_PREFERENCE = "pref_recent_emoji";
    private static final int                   EMOJI_LRU_SIZE       = 50;

    private static void initializeCache(Context context) {
      if (prefs == null) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
      }

      String serialized = prefs.getString(EMOJI_LRU_PREFERENCE, "[]");

      try {
        recentlyUsed = JsonUtils.getMapper().readValue(serialized, TypeFactory.defaultInstance()
                                                                              .constructCollectionType(LinkedHashSet.class, String.class));
      } catch (IOException e) {
        Log.w(TAG, e);
        recentlyUsed = new LinkedHashSet<>();
      }
    }

    public static String[] getRecentlyUsed(Context context) {
      if (recentlyUsed == null) initializeCache(context);
      return recentlyUsed.toArray(new String[recentlyUsed.size()]);
    }

    public static int getRecentlyUsedCount(Context context) {
      if (recentlyUsed == null) initializeCache(context);
      return recentlyUsed.size();
    }

    public static void putRecentlyUsed(Context context, String asset) {
      if (recentlyUsed == null) initializeCache(context);
      if (prefs == null) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
      }

      recentlyUsed.remove(asset);
      recentlyUsed.add(asset);

      if (recentlyUsed.size() > EMOJI_LRU_SIZE) {
        Iterator<String> iterator = recentlyUsed.iterator();
        iterator.next();
        iterator.remove();
      }

      final LinkedHashSet<String> latestRecentlyUsed = new LinkedHashSet<String>(recentlyUsed);
      new AsyncTask<Void, Void, Void>() {

        @Override
        protected Void doInBackground(Void... params) {
          try {
            String serialized = JsonUtils.toJson(latestRecentlyUsed);
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
  }

  public static class EmojiDrawable extends Drawable {
    private final        int    index;
    private final        int    page;
    private final        int    emojiSize;
    private static final Paint  paint;
    private              Bitmap bmp;

    static {
      paint = new Paint();
      paint.setFilterBitmap(true);
    }

    public EmojiDrawable(DrawInfo info, int emojiSize) {
      this.index = info.index;
      this.page = info.page;
      this.emojiSize = emojiSize;
    }

    @Override
    public void draw(Canvas canvas) {
      if (bitmaps[page] == null) {
        Log.w("Emoji", "bitmap for this page was null");
        return;
      }
      if (bmp == null) {
        bmp = bitmaps[page];
      }

      Rect b = copyBounds();
//      int cX = b.centerX(), cY = b.centerY();
//      b.left = cX - emojiSize / 2;
//      b.right = cX + emojiSize / 2;
//      b.top = cY - emojiSize / 2;
//      b.bottom = cY + emojiSize / 2;

      final int row = index / EMOJI_PER_ROW;
      final int row_index = index % EMOJI_PER_ROW;

      canvas.drawBitmap(bmp,
                        new Rect(row_index * emojiSize,
                                 row * emojiSize,
                                 (row_index + 1) * emojiSize,
                                 (row + 1) * emojiSize),
                        b,
                        paint);
    }

    @Override
    public int getOpacity() {
      return 0;
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter cf) { }

    @Override
    public String toString() {
      return "EmojiDrawable{" +
          "page=" + page +
          ", index=" + index +
          '}';
    }
  }

  public static interface PageLoadedListener {
    public void onPageLoaded();
  }

  public static class InvalidatingPageLoadedListener implements PageLoadedListener {
    private final View view;

    public InvalidatingPageLoadedListener(final View view) {
      this.view = view;
    }

    @Override
    public void onPageLoaded() {
      view.post(new Runnable() {
        @Override
        public void run() {
          view.invalidate();
        }
      });
    }

    @Override
    public String toString() {
      return "InvalidatingPageLoadedListener{}";
    }
  }

  public static class DrawInfo {
    int page;
    int index;

    public DrawInfo(final int page, final int index) {
      this.page  = page;
      this.index = index;
    }

    @Override
    public String toString() {
      return "DrawInfo{" +
          "page=" + page +
          ", index=" + index +
          '}';
    }
  }
}
