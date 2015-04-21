/*
 * Copyright (C) 2015 Open Whisper Systems
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

import java.net.URISyntaxException;
import java.net.URLDecoder;

public class Rfc5724Uri {

  private static final String SCHEMA_SMS   = "sms:";
  private static final String SCHEMA_SMSTO = "smsto:";
  private static final String SCHEMA_MMS   = "mms:";
  private static final String SCHEMA_MMSTO = "mmsto:";

  private final String uri;

  public Rfc5724Uri(String uri) throws URISyntaxException {
    this.uri = uri;
    handleValidateUri();
  }

  private void handleValidateUri() throws URISyntaxException {
    String uriLower = uri.toLowerCase();

    if (!uriLower.startsWith(SCHEMA_SMS) && !uriLower.startsWith(SCHEMA_SMSTO) &&
        !uriLower.startsWith(SCHEMA_MMS) && !uriLower.startsWith(SCHEMA_MMSTO))
    {
      throw new URISyntaxException(uri, "supplied URI does not contain a valid schema");
    }

    String[] restrictedTokens = {":", "?"};
    for (String token : restrictedTokens) {
      int tokenIndex = uriLower.indexOf(token);
      if (tokenIndex >= 0 && uriLower.length() > tokenIndex + 1) {
        if (uriLower.indexOf(token, tokenIndex + 1) >= 0) {
          throw new URISyntaxException(uri, "supplied URI contains more than one " + token);
        }
      }
    }

    if (uriLower.contains("&") &&
       (!uriLower.contains("?") || uriLower.indexOf("?") > uriLower.indexOf("&")))
    {
      throw new URISyntaxException(uri, "token & only allowed in query string");
    }

    if (uriLower.indexOf(":") + 1 == getUriWithoutQuery().length()) {
      throw new URISyntaxException(uri, "supplied URI contains no recipients");
    }
  }

  private String getUriWithoutQuery() {
    int queryIndex = uri.indexOf("?");

    if (queryIndex > 0) return uri.substring(0, queryIndex);
    else                return uri;
  }

  public String getSchema() {
    return uri.split(":")[0];
  }

  public String getRecipients() {
    return getUriWithoutQuery().split(":")[1];
  }

  public String getQueryParam(String key) {
    int startIndex  = uri.indexOf(key + "=");
    int startOffset = key.length() + 1;

    if      (startIndex < 0)                             return null;
    else if (uri.length() <= (startIndex + startOffset)) return "";

    int endIndex = uri.indexOf("&", startIndex);

    if (endIndex > startIndex) return URLDecoder.decode(uri.substring(startIndex + startOffset, endIndex));
    else                       return URLDecoder.decode(uri.substring(startIndex + startOffset));
  }

}
