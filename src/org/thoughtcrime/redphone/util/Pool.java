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

package org.thoughtcrime.redphone.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores a collection of objects allowing instances to be checked out and returned.
 *
 * The intent is that this allows reuse of large objects with short lifetimes without loading the
 * GC
 * @param <T> type of object held in the pool
 *
 * @author Stuart O Anderson
 */
//TODO(Stuart Anderson): Refactor audio pipeline to eliminate the need for this
public class Pool<T> {
  List<T> pool = Collections.synchronizedList(new ArrayList<T>());
  Factory<T> itemFactory;

  public Pool( Factory<T> factory) {
    itemFactory = factory;
  }

  public T getItem() {
    T item;
    try {
      item =pool.remove(0);
    } catch( IndexOutOfBoundsException e ) {
      Log.d("Pool", "new Instance");
      return itemFactory.getInstance();
    }
    return item;
  }

  public void returnItem( T item ) {
    pool.add( item );
  }
}
