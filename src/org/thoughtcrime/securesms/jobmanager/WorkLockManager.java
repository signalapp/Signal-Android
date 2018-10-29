package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import androidx.work.Worker;

class WorkLockManager {

  private final Map<UUID, WorkLock> locks = new HashMap<>();

  WorkLock acquire(@NonNull UUID uuid) {
    WorkLock workLock;

    synchronized (this) {
      workLock = locks.get(uuid);

      if (workLock == null) {
        workLock = new WorkLock(uuid);
        locks.put(uuid, workLock);
      }

      workLock.increment();
    }

    workLock.getLock().acquireUninterruptibly();

    return workLock;
  }

  private void release(@NonNull UUID uuid) {
    WorkLock lock;

    synchronized (this) {
      lock = locks.get(uuid);

      if (lock == null) {
        throw new IllegalStateException("Released a lock that was already removed from use.");
      }

      if (lock.decrementAndGet() == 0) {
        locks.remove(uuid);
      }
    }

    lock.getLock().release();
  }

  class WorkLock implements Closeable {

    private final Semaphore lock;
    private final UUID      uuid;

    private Worker.Result result;
    private int           count;

    private WorkLock(@NonNull UUID uuid) {
      this.uuid = uuid;
      this.lock = new Semaphore(1);
    }

    private void increment() {
      count++;
    }

    private int decrementAndGet() {
      count--;
      return count;
    }

    private @NonNull Semaphore getLock() {
      return lock;
    }

    void setResult(@NonNull Worker.Result result) {
      this.result = result;
    }

    @Nullable Worker.Result getResult() {
      return result;
    }

    @Override
    public void close() {
      WorkLockManager.this.release(uuid);
    }
  }
}
