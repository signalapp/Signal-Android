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
package org.whispersystems.libaxolotl;

import java.util.List;

public class InvalidMessageException extends Exception {

  public InvalidMessageException() {}

  public InvalidMessageException(String detailMessage) {
    super(detailMessage);
  }

  public InvalidMessageException(Throwable throwable) {
    super(throwable);
  }

  public InvalidMessageException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public InvalidMessageException(String detailMessage, List<Exception> exceptions) {
    super(detailMessage, exceptions.get(0));
  }
}
