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
package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Medium;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Represents the current and last public key belonging to the "remote"
 * endpoint in an encrypted session.  These are stored on disk.
 *
 * @author Moxie Marlinspike
 */

public class RemoteKeyRecord {

  public static void delete(Context context, CanonicalRecipient recipient) {
    Record.delete(context, Record.SESSIONS_DIRECTORY, getFileNameForRecipient(recipient));
  }

  private static String getFileNameForRecipient(CanonicalRecipient recipient) {
    return recipient.getRecipientId() + "-remote";
  }
}
