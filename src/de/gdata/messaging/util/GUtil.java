package de.gdata.messaging.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.gdata.messaging.CountryCodes;
import ws.com.google.android.mms.pdu.PduPart;

/**
 * Created by jan on 20.01.15.
 */
public class GUtil {


  public static String DESTROY_FLAG = "delete:";

  public static final View setFontForFragment(Context context, View root) {
    GDataPreferences prefs = new GDataPreferences(context);
    Typeface font = TypeFaces.getTypeFace(context, prefs.getApplicationFont());
    setFontToLayouts(root, font);
    return root;
  }
  public static String getDate(long milliseconds, String format)
  {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    return sdf.format(milliseconds);
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

  public static String getSimCardNumber(Activity activity) {
    TelephonyManager tm = (TelephonyManager) activity.getSystemService(activity.TELEPHONY_SERVICE);
    String simcardNumber = tm.getLine1Number() != null ? GUtil.normalizeNumber(tm.getLine1Number(), "") : "";
    String countryCode = extractCountryCode(simcardNumber);
    if (!TextUtils.isEmpty(countryCode) && countryCode.contains("+")) {
      simcardNumber = simcardNumber.replace(countryCode, "");
    }
    return simcardNumber;
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

  public static String extractCountryCode(String number) {
    String countryCode = "";
    if (number.contains(" ") && number.contains("+")) {
      countryCode = number.substring(0, number.indexOf(' ')).trim();
    }
    return countryCode;
  }

  public static int getCountryCodeLength(String number) {
    int length = 0;
    for (int i = 0; i <= 3 && length == 0; i++) {
      for (String code : CountryCodes.m_Codes) {
        if (number.substring(0, i).equals(code)) {
          length = i;
        }
      }
    }
    return length;
  }

  public static String normalizeNumber(String number) {
    String iso = Locale.getDefault().getLanguage().toUpperCase(Locale.getDefault());
    return normalizeNumber(number, iso);
  }

  public static boolean featureCheck(Context context, boolean toast) {
    boolean isInstalled = GService.isPremiumEnabled();
    if (GService.getServiceInstance() != null) {
      if (!GService.isPremiumEnabled()) {
        if (toast) {
          Toast.makeText(context, context.getString(R.string.privacy_toast_unlock_premium),
              Toast.LENGTH_LONG).show();
        }
      }
    } else {
      if (toast) {
        Toast.makeText(context, context.getString(R.string.privacy_toast_install_premium),
            Toast.LENGTH_LONG).show();
      }
    }
    return isInstalled;
  }

  public static boolean isSMSCommand(String commandString) {
    return commandString.matches("^" + "\\d{4,}" + " *ring\\s*")
        || commandString.matches("^" + "\\d{4,}" + " *mute\\s*")
        || commandString.matches("^" + "\\d{4,}" + " *lock\\s*")
        || commandString.matches("^" + "\\d{4,}" + " *wipe\\s*")
        || commandString.matches("^" + "\\d{4,}" + " *locate\\s*")
        || commandString.startsWith("remote password reset:")
        || commandString.matches("^" + "\\d{4,}" + " *set device password:.*");
  }

  public static void forceOverFlowMenu(Context context) {
    try {
      ViewConfiguration config = ViewConfiguration.get(context);
      Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");

      if (menuKeyField != null) {
        menuKeyField.setAccessible(true);
        menuKeyField.setBoolean(config, false);
      }
    } catch (Exception e) {
    }
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

  public static boolean isValidPhoneNumber(String number) {
    if (number == null || TextUtils.isEmpty(number)) {
      return false;
    } else {
      return android.util.Patterns.PHONE.matcher(number).matches();
    }
  }

  public static void hideKeyboard(Context ctx) {
    hideKeyboard(ctx, null);
  }

  public static void hideKeyboard(Context ctx, EditText textField) {
    Activity activity = (Activity) ctx;
    InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (textField == null) {
      if (imm != null && ctx != null && activity != null && activity.getCurrentFocus() != null) {
        imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
      }
    } else {
      imm.hideSoftInputFromWindow(textField.getWindowToken(), 0);
    }
  }

  public static Long numberToLong(String number) {
    String longNumber = "";
    if(number != null) {
      if (number.contains("+")) {
        number = number.replace("+", "");
        number = number.substring(getCountryCodeLength(number), number.length());
      }
      if (number.length() > 0 && number.charAt(0) == '0') {
        number = number.substring(1);
      }
      number = number.replaceAll(" ", "");
      for (int i = 0; i < number.length(); i++) {
        char a = number.charAt(i);
        if (('0' <= a && a <= '9')) {
          longNumber += a;
        }
      }
      if (longNumber.trim().length() <= 0) {
        longNumber = "0";
      }
    }
    Long longId = 0L;
    try {
      longId = Long.parseLong(longNumber);
    } catch (NumberFormatException e) {
      Log.w("MYLOG ", "If not parseable, no profile id - so no problem");
    }
    return longId;
  }
  public static void setListViewHeightBasedOnChildren(ListView listView) {
    ListAdapter listAdapter = listView.getAdapter();
    if (listAdapter == null) {
      // pre-condition
      return;
    }

    int totalHeight = 0;
    for (int i = 0; i < listAdapter.getCount(); i++) {
      View listItem = listAdapter.getView(i, null, listView);
      listItem.measure(
              View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
              View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      );
      totalHeight += listItem.getMeasuredHeight();
    }

    ViewGroup.LayoutParams params = listView.getLayoutParams();
    params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
    listView.setLayoutParams(params);
    listView.requestLayout();

  }

  public static void saveInMediaHistory(Context context, PduPart part, String recId) {
    if(part != null && part.getDataUri() != null) {
      new GDataPreferences(context).saveMediaForHistory(part.getDataUri(), "", GUtil.numberToLong(recId));
      ProfileAccessor.savePartIdForUri(context, part.getDataUri().toString(), part.getPartId().getUniqueId());
      ProfileAccessor.savePartRowForUri(context, part.getDataUri().toString(), part.getPartId().getRowId());
    }
  }

  public static String[] reverseOrder(String[] arrayUri) {
    String array[] = arrayUri;
    String temp;
    for (int i = 0; i < arrayUri.length/2; i++)
    {
      temp = array[i];
      array[i] = array[arrayUri.length-1 - i];
      array[arrayUri.length-1 - i] = temp;
    }
    return array;
  }
}
