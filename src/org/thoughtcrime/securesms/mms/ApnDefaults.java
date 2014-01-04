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

            // Virgin Mobile US - Untested
            put("310053", new MmsConnectionParameters("http://mmsc.vmobl.com:8080/mms", "205.239.233.136", "81"));

            // Slovenia MOBITEL - Untested
            put("29341", new MmsConnectionParameters("http://mms.mobitel.si/servlets/mms", "213.229.249.40", "8080"));

            // Slovenia SI.MOBIL - Untested
            put("29340", new MmsConnectionParameters("http://mmc", "80.95.224.46", "9201"));

            // Slovenia TUSMOBIL - Untested
            put("29370", new MmsConnectionParameters("http://mms.tusmobil.si:8002", "91.185.221.85", "8002"));

            // Slovenia T-2 - Untested
            put("29364", new MmsConnectionParameters("http://www.mms.t-2.net:8002", "172.20.18.137", "8080"));

            // UK giffgaff -Tested
            put("23410", new MmsConnectionParameters("http://mmsc.mediamessaging.co.uk:8002", "193.113.200.195", "8080"));

            // T-Mobile CZ - Untested
            put("23001", new MmsConnectionParameters("http://mms.t-mobile.cz", "010.000.000.010", "80"));

            // O2 CZ - Untestd
            put("23002", new MmsConnectionParameters("http://mms.o2active.cz:8002", "160.218.160.218", "8080"));

            // Vodafone CZ - Untestd
            put("23003", new MmsConnectionParameters("http://mms", "10.11.10.111", "80"));

          }};

  public static MmsConnectionParameters getMmsConnectionParameters(Context context) {
    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    return paramMap.get(tm.getSimOperator());
  }
}
