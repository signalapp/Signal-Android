package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.HashMap;
import java.util.Map;

import static org.thoughtcrime.securesms.mms.MmsCommunication.MmsConnectionParameters;

/**
 * This class provides an in-app source for APN MMSC info for use as a fallback in
 * the event that the system APN DB is unavailable and the user has not provided
 * local MMSC configuration details of their own.
 */
public class ApnDefaults {

  private static final Map<String, MmsConnectionParameters> paramMap =
          new HashMap<String, MmsConnectionParameters>(){{

            //T-Mobile USA - Tested: Works
            put("310260", new MmsConnectionParameters("http://mms.msg.eng.t-mobile.com/mms/wapenc", null, null));

            //AT&T - Untested
            put("310410", new MmsConnectionParameters("http://mmsc.cingular.com/", "wireless.cingular.com", "80"));

            //Verizon - Untested
            put("310004", new MmsConnectionParameters("http://mms.vtext.com/servlets/mms", null, null));
            put("310005", new MmsConnectionParameters("http://mms.vtext.com/servlets/mms", null, null));
            put("310012", new MmsConnectionParameters("http://mms.vtext.com/servlets/mms", null, null));

            //Telenor Norway - Tested
            put("24201", new MmsConnectionParameters("http://mmsc", "10.10.10.11", "8080"));

            // Rogers - Untested
            put("302720", new MmsConnectionParameters("http://mms.gprs.rogers.com", "10.128.1.69", "80"));
          }};

  public static MmsConnectionParameters getMmsConnectionParameters(Context context) {
    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    return paramMap.get(tm.getSimOperator());
  }
}
