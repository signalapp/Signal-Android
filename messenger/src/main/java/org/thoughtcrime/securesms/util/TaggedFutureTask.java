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

package org.thoughtcrime.securesms.util;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * FutureTask with a reference identifier tag.
 *
 * @author Jake McGinty
 */
public class TaggedFutureTask<V> extends FutureTask<V> {
  private final Object tag;
  public TaggedFutureTask(Runnable runnable, V result, Object tag) {
    super(runnable, result);
    this.tag = tag;
  }

  public TaggedFutureTask(Callable<V> callable, Object tag) {
    super(callable);
    this.tag = tag;
  }

  public Object getTag() {
    return tag;
  }
}
