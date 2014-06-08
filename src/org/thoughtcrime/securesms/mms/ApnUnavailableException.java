/**
 * Copyright (C) 2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.mms;

public class ApnUnavailableException extends Exception {

  public ApnUnavailableException() {
  }

  public ApnUnavailableException(String detailMessage) {
    super(detailMessage);
  }

  public ApnUnavailableException(Throwable throwable) {
    super(throwable);
  }

  public ApnUnavailableException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }
}
