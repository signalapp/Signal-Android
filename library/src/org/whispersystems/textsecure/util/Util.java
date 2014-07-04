package org.whispersystems.textsecure.util;

import android.content.Context;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.StateListDrawable;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class Util {

  public static byte[] combine(byte[]... elements) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      for (byte[] element : elements) {
        baos.write(element);
      }

      return baos.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static byte[][] split(byte[] input, int firstLength, int secondLength) {
    byte[][] parts = new byte[2][];

    parts[0] = new byte[firstLength];
    System.arraycopy(input, 0, parts[0], 0, firstLength);

    parts[1] = new byte[secondLength];
    System.arraycopy(input, firstLength, parts[1], 0, secondLength);

    return parts;
  }

  public static byte[][] split(byte[] input, int firstLength, int secondLength, int thirdLength)
      throws ParseException
  {
    if (input == null || firstLength < 0 || secondLength < 0 || thirdLength < 0 ||
        input.length < firstLength + secondLength + thirdLength)
    {
      throw new ParseException("Input too small: " + (input == null ? null : Hex.toString(input)), 0);
    }

    byte[][] parts = new byte[3][];

    parts[0] = new byte[firstLength];
    System.arraycopy(input, 0, parts[0], 0, firstLength);

    parts[1] = new byte[secondLength];
    System.arraycopy(input, firstLength, parts[1], 0, secondLength);

    parts[2] = new byte[thirdLength];
    System.arraycopy(input, firstLength + secondLength, parts[2], 0, thirdLength);

    return parts;
  }

  public static byte[] trim(byte[] input, int length) {
    byte[] result = new byte[length];
    System.arraycopy(input, 0, result, 0, result.length);

    return result;
  }

  public static boolean isEmpty(String value) {
    return value == null || value.trim().length() == 0;
  }

  public static boolean isEmpty(EditText value) {
    return value == null || value.getText() == null || isEmpty(value.getText().toString());
  }

  public static boolean isEmpty(CharSequence value) {
    return value == null || value.length() == 0;
  }


  public static int generateRegistrationId() {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(16380) + 1;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static String getSecret(int size) {
    try {
      byte[] secret = new byte[size];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(secret);
      return Base64.encodeBytes(secret);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError(nsae);
    }
  }

  public static String readFully(File file) throws IOException {
    return readFully(new FileInputStream(file));
  }

  public static String readFully(InputStream in) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buffer              = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1) {
      bout.write(buffer, 0, read);
    }

    in.close();

    return new String(bout.toByteArray());
  }

  public static void readFully(InputStream in, byte[] buffer) throws IOException {
    int offset = 0;

    for (;;) {
      int read = in.read(buffer, offset, buffer.length - offset);

      if (read + offset < buffer.length) offset += read;
      else                		           return;
    }
  }


  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }

    in.close();
    out.close();
  }

  public static String join(Collection<String> list, String delimiter) {
    StringBuilder result = new StringBuilder();
    int i=0;

    for (String item : list) {
      result.append(item);

      if (++i < list.size())
        result.append(delimiter);
    }

    return result.toString();
  }

  public static List<String> split(String source, String delimiter) {
    List<String> results = new LinkedList<String>();

    if (isEmpty(source)) {
      return results;
    }

    String[] elements = source.split(delimiter);

    for (String element : elements) {
      results.add(element);
    }

    return results;
  }

  public static String getDeviceE164Number(Context context) {
    String localNumber = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE))
        .getLine1Number();

    if (!org.whispersystems.textsecure.util.Util.isEmpty(localNumber) &&
        !localNumber.startsWith("+"))
    {
      if (localNumber.length() == 10) localNumber = "+1" + localNumber;
      else                            localNumber = "+"  + localNumber;

      return localNumber;
    }

    return null;
  }

  public static SecureRandom getSecureRandom() {
    try {
      return SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /*
   * source: http://stackoverflow.com/a/9500334
   */
  public static void fixBackgroundRepeat(Drawable bg) {
    if (bg != null) {
      if (bg instanceof BitmapDrawable) {
        BitmapDrawable bmp = (BitmapDrawable) bg;
        bmp.mutate();
        bmp.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
      }
    }
  }
}
