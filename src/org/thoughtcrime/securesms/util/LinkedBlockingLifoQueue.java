package org.thoughtcrime.securesms.util;


import java.util.concurrent.LinkedBlockingDeque;

public class LinkedBlockingLifoQueue<E> extends LinkedBlockingDeque<E> {
  @Override
  public void put(E runnable) throws InterruptedException {
    super.putFirst(runnable);
  }

  @Override
  public boolean add(E runnable) {
    super.addFirst(runnable);
    return true;
  }

  @Override
  public boolean offer(E runnable) {
    super.addFirst(runnable);
    return true;
  }
}
