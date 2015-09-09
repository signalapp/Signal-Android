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

package org.thoughtcrime.redphone.signaling;

/**
 * A helper tuple that encapsulates both a directory filter and
 * hash count, as delivered in a directory update response signal.
 *
 * @author Moxie Marlinspike
 *
 */

public class DirectoryResponse {

  private final int hashCount;
  private final byte[] filter;

  public DirectoryResponse(int hashCount, byte[] filter) {
    this.hashCount = hashCount;
    this.filter    = filter;
  }

  public int getHashCount() {
    return hashCount;
  }

  public byte[] getFilter() {
    return filter;
  }

}
