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
package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

/**
 * Represents a received TextSecureMessage attachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using {@link org.whispersystems.textsecure.api.TextSecureMessageReceiver#retrieveAttachment(TextSecureAttachmentPointer, java.io.File)}
 *
 * @author Moxie Marlinspike
 */
public class TextSecureAttachmentPointer extends TextSecureAttachment {

  private final long              id;
  private final byte[]            key;
  private final Optional<String>  relay;
  private final Optional<Integer> size;
  private final Optional<byte[]>  preview;

  public TextSecureAttachmentPointer(long id, String contentType, byte[] key, String relay) {
    this(id, contentType, key, relay, Optional.<Integer>absent(), Optional.<byte[]>absent());
  }

  public TextSecureAttachmentPointer(long id, String contentType, byte[] key, String relay,
                                     Optional<Integer> size, Optional<byte[]> preview)
  {
    super(contentType);
    this.id      = id;
    this.key     = key;
    this.relay   = Optional.fromNullable(relay);
    this.size    = size;
    this.preview = preview;
  }

  public long getId() {
    return id;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public boolean isStream() {
    return false;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  public Optional<String> getRelay() {
    return relay;
  }

  public Optional<Integer> getSize() {
    return size;
  }

  public Optional<byte[]> getPreview() {
    return preview;
  }
}
