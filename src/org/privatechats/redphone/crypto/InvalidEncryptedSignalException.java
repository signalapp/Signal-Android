/*
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

package org.privatechats.redphone.crypto;

/**
 * Decryption of a signal failed.
 *
 * @author Moxie Marlinspike
 *
 */

public class InvalidEncryptedSignalException extends Exception {

  public InvalidEncryptedSignalException() {
    super();
  }

  public InvalidEncryptedSignalException(String detailMessage) {
    super(detailMessage);
  }

  public InvalidEncryptedSignalException(Throwable throwable) {
    super(throwable);
  }

  public InvalidEncryptedSignalException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

}
