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

  private static final String SCHEMA_SMS   = "sms";
  private static final String SCHEMA_SMSTO = "smsto";
  private static final String SCHEMA_MMS   = "mms";
  private static final String SCHEMA_MMSTO = "mmsto";

  private final String uri;
  private final String schema;
  private final String recipients;

  public Rfc5724Uri(String uri) throws URISyntaxException {
    this.uri        = uri;
    this.schema     = parseSchema(uri);
    this.recipients = parseRecipients(uri);
  }

  private static String parseSchema(String uri) throws URISyntaxException {
    String schema      = uri.split(":")[0];
    String schemaLower = schema.toLowerCase();

    if (!schemaLower.equals(SCHEMA_SMS) && !schemaLower.equals(SCHEMA_SMSTO) &&
        !schemaLower.equals(SCHEMA_MMS) && !schemaLower.equals(SCHEMA_MMSTO))
    {
      throw new URISyntaxException(uri, "supplied URI does not contain a valid schema");
    }

    return schema;
  }

  private static String parseRecipients(String uri) throws URISyntaxException {
    String[] parts = uri.split("\\?")[0].split(":");

    if (parts.length < 2) throw new URISyntaxException(uri, "supplied URI contains no recipients");
    else                  return parts[1];
  }

  public String getSchema() {
    return schema;
  }

  public String getRecipients() {
    return recipients;
  }

  public String getQueryParam(String key) {
    String[] parts = uri.split(key + "=");

    if      (!uri.contains(key + "=")) return null;
    else if (parts.length == 1)        return "";
    else                               return URLDecoder.decode(parts[1].split("&")[0]);
  }
}
