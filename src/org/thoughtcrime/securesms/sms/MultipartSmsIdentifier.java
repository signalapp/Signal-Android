/**
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
package org.thoughtcrime.securesms.sms;


import java.util.HashMap;

public class MultipartSmsIdentifier {

  private static final MultipartSmsIdentifier instance = new MultipartSmsIdentifier();

  public static MultipartSmsIdentifier getInstance() {
    return instance;
  }

  private final HashMap<String, Integer>  idMap = new HashMap<String, Integer>();

  public synchronized byte getIdForRecipient(String recipient) {
    Integer currentId;

    if (idMap.containsKey(recipient)) {
      currentId = idMap.get(recipient);
      idMap.remove(recipient);
    } else {
      currentId = 0;
    }

    byte id  = currentId.byteValue();
    idMap.put(recipient, (currentId + 1) % 255);

    return id;
  }

}
