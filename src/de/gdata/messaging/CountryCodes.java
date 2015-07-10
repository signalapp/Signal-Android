package de.gdata.messaging;

/**
 * Created by jan on 06.07.15.
 */
import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Call getCountry( String code ) to get matching country sign.
 * Call getCode( String country ) to get matching phone code.
 * It has been extended from BaseAdapter in order to make it compatible with Spinner,
 * ListView and so on (just instance it and give it as adapter).
 *
 * This class is provided AS IS without any warranty.
 *
 * @author Niki Romagnoli
 *
 */

public class CountryCodes extends BaseAdapter{

  private static final String[] m_Countries = {
      "AD",
      "AE",
      "AF",
      "AL",
      "AM",
      "AN",
      "AO",
      "AQ",
      "AR",
      "AT",
      "AU",
      "AW",
      "AZ",
      "BA",
      "BD",
      "BE",
      "BF",
      "BG",
      "BH",
      "BI",
      "BJ",
      "BL",
      "BN",
      "BO",
      "BR",
      "BT",
      "BW",
      "BY",
      "BZ",
      "CA",
      "CC",
      "CD",
      "CF",
      "CG",
      "CH",
      "CI",
      "CK",
      "CL",
      "CM",
      "CN",
      "CO",
      "CR",
      "CU",
      "CV",
      "CX",
      "CY",
      "CZ",
      "DE",
      "DJ",
      "DK",
      "DZ",
      "EC",
      "EE",
      "EG",
      "ER",
      "ES",
      "ET",
      "FI",
      "FJ",
      "FK",
      "FM",
      "FO",
      "FR",
      "GA",
      "GB",
      "GE",
      "GH",
      "GI",
      "GL",
      "GM",
      "GN",
      "GQ",
      "GR",
      "GT",
      "GW",
      "GY",
      "HK",
      "HN",
      "HR",
      "HT",
      "HU",
      "ID",
      "IE",
      "IL",
      "IM",
      "IN",
      "IQ",
      "IR",
      "IT",
      "JO",
      "JP",
      "KE",
      "KG",
      "KH",
      "KI",
      "KM",
      "KP",
      "KR",
      "KW",
      "KZ",
      "LA",
      "LB",
      "LI",
      "LK",
      "LR",
      "LS",
      "LT",
      "LU",
      "LV",
      "LY",
      "MA",
      "MC",
      "MD",
      "ME",
      "MG",
      "MH",
      "MK",
      "ML",
      "MM",
      "MN",
      "MO",
      "MR",
      "MT",
      "MU",
      "MV",
      "MW",
      "MX",
      "MY",
      "MZ",
      "NA",
      "NC",
      "NE",
      "NG",
      "NI",
      "NL",
      "NO",
      "NP",
      "NR",
      "NU",
      "NZ",
      "OM",
      "PA",
      "PE",
      "PF",
      "PG",
      "PH",
      "PK",
      "PL",
      "PM",
      "PN",
      "PR",
      "PT",
      "PW",
      "PY",
      "QA",
      "RO",
      "RS",
      "RU",
      "RW",
      "SA",
      "SB",
      "SC",
      "SD",
      "SE",
      "SG",
      "SH",
      "SI",
      "SK",
      "SL",
      "SM",
      "SN",
      "SO",
      "SR",
      "ST",
      "SV",
      "SY",
      "SZ",
      "TD",
      "TG",
      "TH",
      "TJ",
      "TK",
      "TL",
      "TM",
      "TN",
      "TO",
      "TR",
      "TV",
      "TW",
      "TZ",
      "UA",
      "UG",
      "US",
      "UY",
      "UZ",
      "VA",
      "VE",
      "VN",
      "VU",
      "WF",
      "WS",
      "YE",
      "YT",
      "ZA",
      "ZM",
      "ZW"
  };

  public static final String[] m_Codes = {
      "376",
      "971",
      "93",
      "355",
      "374",
      "599",
      "244",
      "672",
      "54",
      "43",
      "61",
      "297",
      "994",
      "387",
      "880",
      "32",
      "226",
      "359",
      "973",
      "257",
      "229",
      "590",
      "673",
      "591",
      "55",
      "975",
      "267",
      "375",
      "501",
      "1",
      "61",
      "243",
      "236",
      "242",
      "41",
      "225",
      "682",
      "56",
      "237",
      "86",
      "57",
      "506",
      "53",
      "238",
      "61",
      "357",
      "420",
      "49",
      "253",
      "45",
      "213",
      "593",
      "372",
      "20",
      "291",
      "34",
      "251",
      "358",
      "679",
      "500",
      "691",
      "298",
      "33",
      "241",
      "44",
      "995",
      "233",
      "350",
      "299",
      "220",
      "224",
      "240",
      "30",
      "502",
      "245",
      "592",
      "852",
      "504",
      "385",
      "509",
      "36",
      "62",
      "353",
      "972",
      "44",
      "91",
      "964",
      "98",
      "39",
      "962",
      "81",
      "254",
      "996",
      "855",
      "686",
      "269",
      "850",
      "82",
      "965",
      "7",
      "856",
      "961",
      "423",
      "94",
      "231",
      "266",
      "370",
      "352",
      "371",
      "218",
      "212",
      "377",
      "373",
      "382",
      "261",
      "692",
      "389",
      "223",
      "95",
      "976",
      "853",
      "222",
      "356",
      "230",
      "960",
      "265",
      "52",
      "60",
      "258",
      "264",
      "687",
      "227",
      "234",
      "505",
      "31",
      "47",
      "977",
      "674",
      "683",
      "64",
      "968",
      "507",
      "51",
      "689",
      "675",
      "63",
      "92",
      "48",
      "508",
      "870",
      "1",
      "351",
      "680",
      "595",
      "974",
      "40",
      "381",
      "7",
      "250",
      "966",
      "677",
      "248",
      "249",
      "46",
      "65",
      "290",
      "386",
      "421",
      "232",
      "378",
      "221",
      "252",
      "597",
      "239",
      "503",
      "963",
      "268",
      "235",
      "228",
      "66",
      "992",
      "690",
      "670",
      "993",
      "216",
      "676",
      "90",
      "688",
      "886",
      "255",
      "380",
      "256",
      "1",
      "598",
      "998",
      "39",
      "58",
      "84",
      "678",
      "681",
      "685",
      "967",
      "262",
      "27",
      "260",
      "263"
  };

  private Context m_Context;

  public CountryCodes( Context cxt )
  {
    super();

    m_Context = cxt;
  }

  /**
   * Get phone code from country sign.
   *
   * @param country: two-chars country sign to fetch ("US", "IT", "GB", ...)
   * @return string of matching phone code ("1", "39", "44", ...). null if none matches.
   */
  public static String getCode( String country )
  {
    int index = getIndex( country );
    return index == -1? null: getCode(index);
  }

  /**
   * Get international code at provided index.
   *
   * @param index: array index
   * @return international code
   */
  public static String getCode( int index )
  {
    return m_Codes[index];
  }

  /**
   * Get country signs from phone code.
   * More countries may match the same code.
   *
   * @param code: phone code to fetch ("1", "39", "44", ...)
   * @return list of uppercase country signs (["US","PR","CA"], ["IT","VA"], ["GB","IM"], ...)
   *          Empty list if none matches.
   */
  public static ArrayList<String> getCountry( String code )
  {
    ArrayList<String> matches = new ArrayList<String>();
    getCountry(code, matches);
    return matches;
  }

  /**
   * Memory cheap version of country fetching: uses user provided list as output which outside
   * could be recycled on desire.
   *
   * @param code: country sign to fetch
   * @param matches: list to fill with matches, used as output
   */
  public static void getCountry( String code, ArrayList<String> matches )
  {
    matches.clear();

    for( int i=0; i<m_Codes.length; ++i )
      if( m_Codes[i].equals( code ) )
        matches.add(getCountry(i));
  }

  /**
   * Returns country sign at specified index of internal array.
   *
   * @param index: index to fetch
   * @return country sign
   */
  public static String getCountry( int index )
  {
    return m_Countries[index];
  }

  /**
   * Looks for country sign array index.
   *
   * @param country: country sign to search
   * @return array index. -1 if none matches.
   */
  public static int getIndex( String country )
  {
    String search = country.toUpperCase(Locale.getDefault());

    for( int i=0; i<m_Countries.length; ++i )
      if( m_Countries[i].equals( search ) )
        return i;

    return -1;
  }

  @Override
  public int getCount() {
    return m_Codes.length;
  }

  @Override
  public Object getItem(int index) {
    return m_Countries[index];
  }

  @Override
  public long getItemId(int index) {
    return index;
  }

  @Override
  public View getView(int index, View recycleView, ViewGroup viewGroup) {
    TextView view;
    if( recycleView == null )
    {
      view = new TextView(m_Context);
      view.setPadding(30, 10, 10, 10);
    }
    else
    {
      view = (TextView)recycleView;
    }

    view.setText(m_Countries[index]);

    return view;
  }
}