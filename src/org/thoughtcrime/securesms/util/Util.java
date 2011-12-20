/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

public class Util {

  public static byte[] combine(byte[] one, byte[] two) {
    byte[] combined = new byte[one.length + two.length];
    System.arraycopy(one, 0, combined, 0, one.length);
    System.arraycopy(two, 0, combined, one.length, two.length);

    return combined;
  }
	
  public static byte[] combine(byte[] one, byte[] two, byte[] three) {
    byte[] combined = new byte[one.length + two.length + three.length];
    System.arraycopy(one, 0, combined, 0, one.length);
    System.arraycopy(two, 0, combined, one.length, two.length);
    System.arraycopy(three, 0, combined, one.length + two.length, three.length);
		
    return combined;
  }
	
  public static byte[] combine(byte[] one, byte[] two, byte[] three, byte[] four) {
    byte[] combined = new byte[one.length + two.length + three.length + four.length];
    System.arraycopy(one, 0, combined, 0, one.length);
    System.arraycopy(two, 0, combined, one.length, two.length);
    System.arraycopy(three, 0, combined, one.length + two.length, three.length);
    System.arraycopy(four, 0, combined, one.length + two.length + three.length, four.length);
		
    return combined;
		
  }
	
  public static String[] splitString(String string, int maxLength) {
    int count = string.length() / maxLength;
		
    if (string.length() % maxLength > 0)
      count++;
		
    String[] splitString = new String[count];
		
    for (int i=0;i<count-1;i++)
      splitString[i] = string.substring(i*maxLength, (i*maxLength) + maxLength);
		
    splitString[count-1] = string.substring((count-1) * maxLength);
		
    return splitString;
  }
	
  //	public static Bitmap loadScaledBitmap(InputStream src, int targetWidth, int targetHeight) {
  //		return BitmapFactory.decodeStream(src);
  ////		BitmapFactory.Options options = new BitmapFactory.Options();
  ////		options.inJustDecodeBounds    = true;
  ////		BitmapFactory.decodeStream(src, null, options);
  ////
  ////		Log.w("Util", "Bitmap Origin Width: " + options.outWidth);
  ////		Log.w("Util", "Bitmap Origin Height: " + options.outHeight);
  ////		
  ////		boolean scaleByHeight = 
  ////			Math.abs(options.outHeight - targetHeight) >= 
  ////			Math.abs(options.outWidth - targetWidth);
  ////
  ////		if (options.outHeight * options.outWidth >= targetWidth * targetHeight * 2) {
  ////			double sampleSize = scaleByHeight ? (double)options.outHeight / (double)targetHeight : (double)options.outWidth / (double)targetWidth;
  //////			options.inSampleSize = (int)Math.pow(2d, Math.floor(Math.log(sampleSize) / Math.log(2d)));
  ////			Log.w("Util", "Sampling by: " + options.inSampleSize);
  ////		}
  ////
  ////		 options.inJustDecodeBounds = false;
  ////		 
  ////		 return BitmapFactory.decodeStream(src, null, options);
  //	}

}
