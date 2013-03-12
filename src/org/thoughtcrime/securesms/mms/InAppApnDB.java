package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cmorris
 * Date: 3/10/13
 * Time: 2:23 PM
 */
public class InAppApnDB {

    private static final Map<String, String> mmscMap = new HashMap<String, String>();
    private static final Map<String, String> proxyMap = new HashMap<String, String>();
    private static final Map<String, String> proxyPortMap = new HashMap<String, String>();

    private String mccmnc;

    static {
        //T-Mobile USA - Tested: Works
        mmscMap.put("310260", "http://mms.msg.eng.t-mobile.com/mms/wapenc");

        //AT&T - Untested
        mmscMap.put("310410", "http://mmsc.cingular.com/");
        proxyMap.put("310410", "wireless.cingular.com");
        proxyPortMap.put("310410", "80");

        //Verizon - Untested
        mmscMap.put("310004", "http://mms.vtext.com/servlets/mms");
        mmscMap.put("310005", "http://mms.vtext.com/servlets/mms");
        mmscMap.put("310012", "http://mms.vtext.com/servlets/mms");
    }

    public InAppApnDB(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.mccmnc = tm.getSimOperator();
    }

    public String getMmsc() {
        return mmscMap.get(mccmnc);
    }

    public String getProxy() {
        return proxyMap.get(mccmnc);
    }

    public String getProxyPort() {
        return proxyPortMap.get(mccmnc);
    }
}
