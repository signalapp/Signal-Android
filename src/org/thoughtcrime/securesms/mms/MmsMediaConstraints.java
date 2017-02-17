package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedHashMap;

public class MmsMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 1024;
  public  static int MAX_MESSAGE_SIZE             = 280 * 1024;

  @Override
  public int getImageMaxWidth(Context context) {
    return Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public int getImageMaxSize(Context context) {
    return getCarrierMax(context);
  }

  @Override
  public int getGifMaxSize(Context context) {
    return getCarrierMax(context);
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return getCarrierMax(context);
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return getCarrierMax(context);
  }

  private int getCarrierMax(Context context)
  {
    LegacyMmsConnection.Apn a;
    String mmscUrl;
    try {
      a = LegacyMmsConnection.getApn(context);
      mmscUrl = a.getMmsc();
    } catch (ApnUnavailableException aue)
    {
      return MAX_MESSAGE_SIZE;
    }

    String[] keys = context.getResources().getStringArray(R.array.CarrierConstraintKeys);
    String[] values = context.getResources().getStringArray(R.array.CarrierConstraintValues);
    LinkedHashMap<String,String> map = new LinkedHashMap<String,String>();
    for (int i = 0; i < Math.min(keys.length, values.length); ++i) {
      map.put(keys[i], values[i]);
    }
    if(map.containsKey(mmscUrl)){
      MAX_MESSAGE_SIZE = Integer.parseInt(map.get(mmscUrl)) * 1024;
    }

    return MAX_MESSAGE_SIZE;
  }
}
