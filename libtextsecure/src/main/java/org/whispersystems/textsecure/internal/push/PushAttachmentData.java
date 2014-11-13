/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.whispersystems.textsecure.internal.push;

import java.io.InputStream;

public class PushAttachmentData {

  private final String      contentType;
  private final InputStream data;
  private final long        dataSize;
  private final byte[]      key;

  public PushAttachmentData(String contentType, InputStream data, long dataSize, byte[] key) {
    this.contentType = contentType;
    this.data        = data;
    this.dataSize    = dataSize;
    this.key         = key;
  }

  public String getContentType() {
    return contentType;
  }

  public InputStream getData() {
    return data;
  }

  public long getDataSize() {
    return dataSize;
  }

  public byte[] getKey() {
    return key;
  }
}
