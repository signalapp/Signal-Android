package de.gdata.messaging.util;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jan on 20.01.15.
 */
public class GUtil {


  public static final View setFontForFragment(Context context, View root) {
    GDataPreferences prefs = new GDataPreferences(context);
    Typeface font = TypeFaces.getTypeFace(context, prefs.getApplicationFont());
    setFontToLayouts(root, font);
    return root;
  }

  /**
   * Sets the Typeface e.g. Roboto-Thin.tff for an Activity
   *
   * @param container parent View containing the TextViews
   * @param font      Typeface to set
   */
  public static final void setFontToLayouts(Object container, Typeface font) {
    if (container == null || font == null) return;

    if (container instanceof View) {
      if (container instanceof TextView) {
        ((TextView) container).setTypeface(font);
      } else if (container instanceof LinearLayout) {
        final int count = ((LinearLayout) container).getChildCount();
        for (int i = 0; i <= count; i++) {
          final View child = ((LinearLayout) container).getChildAt(i);
          if (child instanceof TextView) {
            // Set the font if it is a TextView.
            ((TextView) child).setTypeface(font);
          } else if (child instanceof ViewGroup) {
            // Recursively attempt another ViewGroup.
            setFontToLayouts(child, font);
          }
        }
      } else if (container instanceof FrameLayout) {
        final int count = ((FrameLayout) container).getChildCount();
        for (int i = 0; i <= count; i++) {
          final View child = ((FrameLayout) container).getChildAt(i);
          if (child instanceof TextView) {
            ((TextView) child).setTypeface(font);
          } else if (child instanceof ViewGroup) {
            setFontToLayouts(child, font);
          }
        }
      } else if (container instanceof RelativeLayout) {
        final int count = ((RelativeLayout) container).getChildCount();
        for (int i = 0; i <= count; i++) {
          final View child = ((RelativeLayout) container).getChildAt(i);
          if (child instanceof TextView) {
            ((TextView) child).setTypeface(font);
          } else if (child instanceof ViewGroup) {
            setFontToLayouts(child, font);
          }
        }
      }

    } else if (container instanceof ViewGroup) {
      final int count = ((ViewGroup) container).getChildCount();
      for (int i = 0; i <= count; i++) {
        final View child = ((ViewGroup) container).getChildAt(i);
        if (child instanceof TextView) {
          ((TextView) child).setTypeface(font);
        } else if (child instanceof ViewGroup) {
          setFontToLayouts(child, font);
        }
      }
    }
  }

  public static ArrayList<String> extractUrls(String input) {
    ArrayList<String> result = new ArrayList<String>();

    Pattern pattern = Pattern.compile(
        "\\b(((ht|f)tp(s?)\\:\\/\\/|~\\/|\\/)|www.)" +
            "(\\w+:\\w+@)?(([-\\w]+\\.)+(com|org|net|gov" +
            "|mil|biz|info|mobi|name|aero|jobs|museum" +
            "|travel|link|[a-z]{2}))(:[\\d]{1,5})?" +
            "(((\\/([-\\w~!$+|.,=]|%[a-f\\d]{2})+)+|\\/)+|\\?|#)?" +
            "((\\?([-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
            "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)" +
            "(&(?:[-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
            "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)*)*" +
            "(#([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)?\\b");

    Matcher matcher = pattern.matcher(input.toLowerCase());
    while (matcher.find()) {
      result.add(matcher.group());
    }

    return result;
  }

  /**
   * Generates placeholder String for in queries i.e.: "id in (" + buildInPlaceholders(5) + ")"
   */
  public static String buildInPlaceholders(int length) {
    int strLen = 3 * length - 2;
    StringBuilder sb = new StringBuilder(0);
    if (length > 0) {
      sb = new StringBuilder(strLen);
      for (int i = 1; i <= length; i++) {
        if (i < length) {
          sb.append("?, ");
        } else {
          sb.append("?");
        }
      }
    }
    return sb.toString();
  }

  public static void normalizeNumbers(String[] numbers) {
    String iso = Locale.getDefault().getLanguage().toUpperCase(Locale.getDefault());
    for (int i = 0; i < numbers.length; i++) {
      String phoneNo = numbers[i];
      numbers[i] = normalizeNumber(phoneNo, iso);
    }
  }

  public static String normalizeNumber(String number, String iso) {
    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    String phoneNo = "";
    try {
      Phonenumber.PhoneNumber phone = phoneUtil.parse(number, iso);
      phoneNo = phoneUtil.format(phone, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    } catch (NumberParseException e) {
      return number;
    }
    return phoneNo;
  }

  public static String normalizeNumber(String number) {
    String iso = Locale.getDefault().getLanguage().toUpperCase(Locale.getDefault());
    return normalizeNumber(number, iso);
  }

  public static void normalizeNumbers(List<String> numbers) {
    String iso = Locale.getDefault().getLanguage().toUpperCase(Locale.getDefault());
    for (int i = 0; i < numbers.size(); i++) {
      String phoneNo = numbers.get(i);
      numbers.set(i, normalizeNumber(phoneNo, iso));
    }
  }

  public static boolean featureCheck(Context context, boolean toast) {
    boolean isInstalled = new GDataPreferences(context).isPremiumInstalled();
    if (!isInstalled) {
      if (toast) {
        Toast.makeText(context, context.getString(R.string.privacy_toast_install_premium),
            Toast.LENGTH_LONG).show();
      }
    }
    return isInstalled;
  }

  public static String[] addStringArray(String[] a, String[] b) {
    int aLen = a.length;
    int bLen = b != null ? b.length : 0;
    b = b == null ? new String[]{} : b;
    String[] c = new String[aLen + bLen];
    System.arraycopy(a, 0, c, 0, aLen);
    System.arraycopy(b, 0, c, aLen, bLen);
    return c;
  }
}
