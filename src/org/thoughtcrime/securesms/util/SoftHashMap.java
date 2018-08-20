/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.thoughtcrime.securesms.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A <code><em>Soft</em>HashMap</code> is a memory-constrained map that stores its <em>values</em> in
 * {@link SoftReference SoftReference}s.  (Contrast this with the JDK's
 * {@link WeakHashMap WeakHashMap}, which uses weak references for its <em>keys</em>, which is of little value if you
 * want the cache to auto-resize itself based on memory constraints).
 * <p/>
 * Having the values wrapped by soft references allows the cache to automatically reduce its size based on memory
 * limitations and garbage collection.  This ensures that the cache will not cause memory leaks by holding strong
 * references to all of its values.
 * <p/>
 * This class is a generics-enabled Map based on initial ideas from Heinz Kabutz's and Sydney Redelinghuys's
 * <a href="http://www.javaspecialists.eu/archive/Issue015.html">publicly posted version (with their approval)</a>, with
 * continued modifications.
 * <p/>
 * This implementation is thread-safe and usable in concurrent environments.
 *
 * @since 1.0
 */
public class SoftHashMap<K, V> implements Map<K, V> {

  /**
   * The default value of the RETENTION_SIZE attribute, equal to 100.
   */
  private static final int DEFAULT_RETENTION_SIZE = 100;

  /**
   * The internal HashMap that will hold the SoftReference.
   */
  private final Map<K, SoftValue<V, K>> map;

  /**
   * The number of strong references to hold internally, that is, the number of instances to prevent
   * from being garbage collected automatically (unlike other soft references).
   */
  private final int RETENTION_SIZE;

  /**
   * The FIFO list of strong references (not to be garbage collected), order of last access.
   */
  private final Queue<V> strongReferences; //guarded by 'strongReferencesLock'
  private final ReentrantLock strongReferencesLock;

  /**
   * Reference queue for cleared SoftReference objects.
   */
  private final ReferenceQueue<? super V> queue;

  /**
   * Creates a new SoftHashMap with a default retention size size of
   * {@link #DEFAULT_RETENTION_SIZE DEFAULT_RETENTION_SIZE} (100 entries).
   *
   * @see #SoftHashMap(int)
   */
  public SoftHashMap() {
    this(DEFAULT_RETENTION_SIZE);
  }

  /**
   * Creates a new SoftHashMap with the specified retention size.
   * <p/>
   * The retention size (n) is the total number of most recent entries in the map that will be strongly referenced
   * (ie 'retained') to prevent them from being eagerly garbage collected.  That is, the point of a SoftHashMap is to
   * allow the garbage collector to remove as many entries from this map as it desires, but there will always be (n)
   * elements retained after a GC due to the strong references.
   * <p/>
   * Note that in a highly concurrent environments the exact total number of strong references may differ slightly
   * than the actual <code>retentionSize</code> value.  This number is intended to be a best-effort retention low
   * water mark.
   *
   * @param retentionSize the total number of most recent entries in the map that will be strongly referenced
   *                      (retained), preventing them from being eagerly garbage collected by the JVM.
   */
  @SuppressWarnings({"unchecked"})
  public SoftHashMap(int retentionSize) {
    super();
    RETENTION_SIZE = Math.max(0, retentionSize);
    queue = new ReferenceQueue<V>();
    strongReferencesLock = new ReentrantLock();
    map = new ConcurrentHashMap<K, SoftValue<V, K>>();
    strongReferences = new ConcurrentLinkedQueue<V>();
  }

  /**
   * Creates a {@code SoftHashMap} backed by the specified {@code source}, with a default retention
   * size of {@link #DEFAULT_RETENTION_SIZE DEFAULT_RETENTION_SIZE} (100 entries).
   *
   * @param source the backing map to populate this {@code SoftHashMap}
   * @see #SoftHashMap(Map,int)
   */
  public SoftHashMap(Map<K, V> source) {
    this(DEFAULT_RETENTION_SIZE);
    putAll(source);
  }

  /**
   * Creates a {@code SoftHashMap} backed by the specified {@code source}, with the specified retention size.
   * <p/>
   * The retention size (n) is the total number of most recent entries in the map that will be strongly referenced
   * (ie 'retained') to prevent them from being eagerly garbage collected.  That is, the point of a SoftHashMap is to
   * allow the garbage collector to remove as many entries from this map as it desires, but there will always be (n)
   * elements retained after a GC due to the strong references.
   * <p/>
   * Note that in a highly concurrent environments the exact total number of strong references may differ slightly
   * than the actual <code>retentionSize</code> value.  This number is intended to be a best-effort retention low
   * water mark.
   *
   * @param source        the backing map to populate this {@code SoftHashMap}
   * @param retentionSize the total number of most recent entries in the map that will be strongly referenced
   *                      (retained), preventing them from being eagerly garbage collected by the JVM.
   */
  public SoftHashMap(Map<K, V> source, int retentionSize) {
    this(retentionSize);
    putAll(source);
  }

  public V get(Object key) {
    processQueue();

    V result = null;
    SoftValue<V, K> value = map.get(key);

    if (value != null) {
      //unwrap the 'real' value from the SoftReference
      result = value.get();
      if (result == null) {
        //The wrapped value was garbage collected, so remove this entry from the backing map:
        //noinspection SuspiciousMethodCalls
        map.remove(key);
      } else {
        //Add this value to the beginning of the strong reference queue (FIFO).
        addToStrongReferences(result);
      }
    }
    return result;
  }

  private void addToStrongReferences(V result) {
    strongReferencesLock.lock();
    try {
      strongReferences.add(result);
      trimStrongReferencesIfNecessary();
    } finally {
      strongReferencesLock.unlock();
    }

  }

  //Guarded by the strongReferencesLock in the addToStrongReferences method

  private void trimStrongReferencesIfNecessary() {
    //trim the strong ref queue if necessary:
    while (strongReferences.size() > RETENTION_SIZE) {
      strongReferences.poll();
    }
  }

  /**
   * Traverses the ReferenceQueue and removes garbage-collected SoftValue objects from the backing map
   * by looking them up using the SoftValue.key data member.
   */
  private void processQueue() {
    SoftValue sv;
    while ((sv = (SoftValue) queue.poll()) != null) {
      //noinspection SuspiciousMethodCalls
      map.remove(sv.key); // we can access private data!
    }
  }

  public boolean isEmpty() {
    processQueue();
    return map.isEmpty();
  }

  public boolean containsKey(Object key) {
    processQueue();
    return map.containsKey(key);
  }

  public boolean containsValue(Object value) {
    processQueue();
    Collection values = values();
    return values != null && values.contains(value);
  }

  public void putAll(Map<? extends K, ? extends V> m) {
    if (m == null || m.isEmpty()) {
      processQueue();
      return;
    }
    for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  public Set<K> keySet() {
    processQueue();
    return map.keySet();
  }

  public Collection<V> values() {
    processQueue();
    Collection<K> keys = map.keySet();
    if (keys.isEmpty()) {
      //noinspection unchecked
      return Collections.EMPTY_SET;
    }
    Collection<V> values = new ArrayList<V>(keys.size());
    for (K key : keys) {
      V v = get(key);
      if (v != null) {
        values.add(v);
      }
    }
    return values;
  }

  /**
   * Creates a new entry, but wraps the value in a SoftValue instance to enable auto garbage collection.
   */
  public V put(K key, V value) {
    processQueue(); // throw out garbage collected values first
    SoftValue<V, K> sv = new SoftValue<V, K>(value, key, queue);
    SoftValue<V, K> previous = map.put(key, sv);
    addToStrongReferences(value);
    return previous != null ? previous.get() : null;
  }

  public V remove(Object key) {
    processQueue(); // throw out garbage collected values first
    SoftValue<V, K> raw = map.remove(key);
    return raw != null ? raw.get() : null;
  }

  public void clear() {
    strongReferencesLock.lock();
    try {
      strongReferences.clear();
    } finally {
      strongReferencesLock.unlock();
    }
    processQueue(); // throw out garbage collected values
    map.clear();
  }

  public int size() {
    processQueue(); // throw out garbage collected values first
    return map.size();
  }

  public Set<Map.Entry<K, V>> entrySet() {
    processQueue(); // throw out garbage collected values first
    Collection<K> keys = map.keySet();
    if (keys.isEmpty()) {
      //noinspection unchecked
      return Collections.EMPTY_SET;
    }

    Map<K, V> kvPairs = new HashMap<K, V>(keys.size());
    for (K key : keys) {
      V v = get(key);
      if (v != null) {
        kvPairs.put(key, v);
      }
    }
    return kvPairs.entrySet();
  }

  /**
   * We define our own subclass of SoftReference which contains
   * not only the value but also the key to make it easier to find
   * the entry in the HashMap after it's been garbage collected.
   */
  private static class SoftValue<V, K> extends SoftReference<V> {

    private final K key;

    /**
     * Constructs a new instance, wrapping the value, key, and queue, as
     * required by the superclass.
     *
     * @param value the map value
     * @param key   the map key
     * @param queue the soft reference queue to poll to determine if the entry had been reaped by the GC.
     */
    private SoftValue(V value, K key, ReferenceQueue<? super V> queue) {
      super(value, queue);
      this.key = key;
    }

  }
}