package org.thoughtcrime.securesms.groups;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by christoph on 29.04.15.
 */
public class Conversations {
  private Set<Long> batchSet;

  public Conversations(Set<Long> batchSet) {
    this.batchSet = batchSet;
  }

  public long[] asArray() {
    long[] ids = new long[batchSet.size()];
    int i = 0;
    Iterator<Long> iterator = batchSet.iterator();
    while (iterator.hasNext()) {
      ids[i++] = iterator.next();
    }
    return ids;
  }

  public Set<Long> asSet() {
    return batchSet;
  }

  public int size() {
    return batchSet.size();
  }

  public boolean isEmpty() {
    return batchSet.isEmpty();
  }
}
