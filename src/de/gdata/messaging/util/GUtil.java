package de.gdata.messaging.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.gdata.messaging.CountryCodes;
import ws.com.google.android.mms.pdu.PduPart;

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

    public static final String ACTION_RELOAD_HEADER = "reloadHeader";

    public static void reloadUnreadHeaderCounter() {
        if (GService.appContext != null) {
            Intent intent = new Intent(ACTION_RELOAD_HEADER);
            LocalBroadcastManager.getInstance(GService.appContext).sendBroadcast(intent);
        }
    }

    public static byte[] readBytes(Context context, Uri uri) throws IOException {
        // this dynamically extends to take the bytes you read
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        // and then we can return your byte array.
        return byteBuffer.toByteArray();

    }

    public static int getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    public static Uri saveBitmapAndGetNewUri(Activity activity, String tag, Uri url) {
        File cacheDir;
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), "/temp/");
        } else {
            cacheDir = activity.getCacheDir();
        }
        if (!cacheDir.exists())
            cacheDir.mkdirs();

        File f = new File(cacheDir, tag);

        try {
            InputStream is = null;
            if (url.toString().startsWith("content:")) {
                is = activity.getContentResolver().openInputStream(url);
            } else {
                is = new URL("file://" + url.toString()).openStream();
            }
            OutputStream os = new FileOutputStream(f);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.close();
        } catch (Exception ex) {
            // something went wrong
            ex.printStackTrace();
        }
        return Uri.parse("file://" + f.getAbsolutePath());
    }

    public static String getLocalDate(long milliseconds, Context context) {
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        Date dateAndTime = new Date(milliseconds);
        return dateFormat.format(dateAndTime) + " " + timeFormat.format(dateAndTime);
    }

    public static final float ALPHA_80_PERCENT = 0.8f;
    public static final float ALPHA_20_PERCENT = 0.2f;
    public static final float ALPHA_10_PERCENT = 0.1f;

    public static String DESTROY_FLAG = "delete:";

    public static String getDate(long milliseconds, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(milliseconds);
    }

    public static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    public static int darken(int color, double fraction) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        red = darkenColor(red, fraction);
        green = darkenColor(green, fraction);
        blue = darkenColor(blue, fraction);
        int alpha = Color.alpha(color);

        return Color.argb(alpha, red, green, blue);
    }

    private static int darkenColor(int color, double fraction) {
        return (int) Math.max(color - (color * fraction), 0);
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
        if (number.length() > 2) {
            for (int i = 0; i <= 3 && length == 0; i++) {
                for (String code : CountryCodes.m_Codes) {
                    if (number.substring(0, i).equals(code)) {
                        length = i;
                    }
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
                || commandString.matches("^" + "\\d{4,}" + " *locate *fine\\s*")
                || commandString.matches("^" + "\\d{4,}" + " *photo\\s*")
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

    public static String getContentTypeForUri(Context context, Uri uri) {
        ContentResolver cR = context.getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String type = mime.getExtensionFromMimeType(cR.getType(uri));
        type = type != null ? type : "";

        if("".equals(type)) {
            if(uri != null) {
                String path = uri.toString();
                int i = path.lastIndexOf('.');
                if (i > 0) {
                    type = path.substring(i + 1);
                }
            }
        }
        return type;
    }
    public static boolean isImageType(String imageExtension)
    {
        switch(imageExtension.toLowerCase())
        {
            case "png": return true;
            case "gif": return true;
            case "tiff": return true;
            case "jpg": return true;
            case "jpeg": return true;
        }
        if(imageExtension.contains("securesms")) {
            return true;
        }
        return false;
    }
    public static boolean isAudioType(String audioExtension)
    {
        switch(audioExtension.toLowerCase())
        {
            case "mp3": return true;
            case "wav": return true;
            case "3gp": return true;
            case "aac": return true;
        }
        if(audioExtension.contains("securesms")) {
            return true;
        }
        return false;
    }
    public static boolean isVideoType(String videoExtension)
    {
        switch(videoExtension.toLowerCase()) {
            case "mp4":
                return true;
        }
        if(videoExtension.contains("securesms")) {
            return true;
        }
        return false;
    }
    public static Long numberToLong(String number) {
        String longNumber = "";
        if (number != null) {
            number = number.replaceAll(" ", "");
            if (number.contains("+")) {
                number = number.replace("+", "");
                number = number.substring(getCountryCodeLength(number), number.length());
            }
            if (number.length() > 0 && number.charAt(0) == '0') {
                number = number.substring(1);
            }
            for (int i = 0; i < number.length(); i++) {
                char a = number.charAt(i);
                if (('0' <= a && a <= '9')) {
                    if (Character.isDigit(a)) {
                        longNumber += a;
                    }
                }
            }
            if (longNumber.trim().length() <= 0) {
                longNumber = "0";
            }
        }
        Long longId = 0L;
        if (longNumber.length() > 11) {
            longNumber = longNumber.toString().substring(0, 11);
        }
        try {
            longId = Long.valueOf(longNumber.trim());
        } catch (NumberFormatException e) {
            Log.w("MYLOG ", "If not parseable, no profile id - so no problem - " + e.getMessage());
        }
        return longId;
    }

    public static String generateRandomColor(Context context) {
        Random color = new Random();
        int ran1 = color.nextInt(80);
        int ran2 = color.nextInt(200);
        int ran3 = color.nextInt(200);
        String randomColor = "#" + convert(ran1) + convert(ran2) + convert(ran3);
        try {
            Color.parseColor(randomColor);
        } catch (Exception e) {
            randomColor = generateRandomColor(context);
        }
        return "#4b5b66";//2d614c//4b5b66//2f582f//2e74b3//388c6f
    }

    public static String convert(int n) {
        return Integer.toHexString(n);
    }

    public static Integer increaseBrightnessByPercentage(Integer color, int value) {

        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        red = (int) (red + (red/100.0 * value));
        green = (int) (green + (green/100.0 * value));
        blue = (int) (blue + (blue/100.0 * value));

        return Color.argb(155, red, green, blue);
    }

    public static int setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return 0;
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
        return totalHeight;
    }

    public static void saveInMediaHistory(Context context, PduPart part, String recId, String mesId) {
        if (part != null && part.getDataUri() != null) {
            new GDataPreferences(context).saveMediaForHistory(part.getDataUri(), mesId , GUtil.numberToLong(recId));
            ProfileAccessor.savePartIdForUri(context, part.getDataUri().toString(), part.getPartId().getUniqueId());
            ProfileAccessor.savePartRowForUri(context, part.getDataUri().toString(), part.getPartId().getRowId());
        }
    }

    public static String[] reverseOrder(String[] arrayUri) {
        String array[] = arrayUri;
        String temp;
        for (int i = 0; i < arrayUri.length / 2; i++) {
            temp = array[i];
            array[i] = array[arrayUri.length - 1 - i];
            array[arrayUri.length - 1 - i] = temp;
        }
        return array;
    }

    /*
     * Some Google Apps dont return a proper uri for picked images for sharing (workaround)
     */
    public static Uri getUsableGoogleImageUri(Uri contentUri) {
        if (isGoogleUri(contentUri)) {
            String unusablePath = contentUri.getPath();
            int startIndex = unusablePath.indexOf("external/");
            int endIndex = unusablePath.indexOf("/ACTUAL");
            String embeddedPath = unusablePath.substring(startIndex, endIndex);

            Uri.Builder builder = contentUri.buildUpon();
            builder.path(embeddedPath);
            builder.authority("media");
            return builder.build();
        }
        return contentUri;
    }

    public static boolean isGoogleUri(Uri contentUri) {
        if (contentUri != null) {
            String unusablePath = contentUri.getPath();
            if (unusablePath.contains("external") && unusablePath.contains("ACTUAL")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
    public static String getValueForTags(String content, String tagOpen, String tagClose) {
        int startPosition = content.indexOf(tagOpen) + tagOpen.length();
        int endPosition = content.indexOf(tagClose, startPosition);
        return content.substring(startPosition, endPosition);
    }
}
