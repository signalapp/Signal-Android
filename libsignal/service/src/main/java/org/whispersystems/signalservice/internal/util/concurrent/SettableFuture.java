package org.whispersystems.signalservice.internal.util.concurrent;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettableFuture<T> implements ListenableFuture<T> {
    private static final String TAG = SettableFuture.class.getSimpleName();
    private final List<Listener<T>> listeners = new LinkedList<>();

    private boolean completed;
    private boolean canceled;
    private volatile T result;
    private volatile Throwable exception;

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (!completed && !canceled) {
            canceled = true;
            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean isCancelled() {
        return canceled;
    }

    @Override
    public synchronized boolean isDone() {
        return completed;
    }

    public boolean set(T result) {
        Log.w(TAG, "set");
        synchronized (this) {
            if (completed || canceled) return false;

            this.result = result;
            this.completed = true;

            notifyAll();
        }

        notifyAllListeners();
        return true;
    }

    public boolean setException(Throwable throwable) {
        Log.w(TAG, "setException");
        synchronized (this) {
            if (completed || canceled) return false;

            this.exception = throwable;
            this.completed = true;

            notifyAll();
        }

        notifyAllListeners();
        return true;
    }

    @Override
    public synchronized T get() throws InterruptedException, ExecutionException {
        Log.w(TAG, "get");
        while (!completed) wait();

        if (exception != null) throw new ExecutionException(exception);
        else return result;
    }

    @Override
    public synchronized T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Log.w(TAG, "get");
        long startTime = System.currentTimeMillis();

        while (!completed && System.currentTimeMillis() - startTime < unit.toMillis(timeout)) {
            wait(unit.toMillis(timeout));
        }

        if (!completed) throw new TimeoutException();
        else return get();
    }

    @Override
    public void addListener(Listener<T> listener) {
        Log.w(TAG, "addListener");
        synchronized (this) {
            listeners.add(listener);

            if (!completed) return;
        }

        notifyListener(listener);
    }

    private void notifyAllListeners() {
        Log.w(TAG, "notifyAllListeners");
        List<Listener<T>> localListeners;

        synchronized (this) {
            localListeners = new LinkedList<>(listeners);
        }

        for (Listener<T> listener : localListeners) {
            notifyListener(listener);
        }
    }

    private void notifyListener(Listener<T> listener) {
        Log.w(TAG, "notifyListener");
        if (exception != null) listener.onFailure(new ExecutionException(exception));
        else listener.onSuccess(result);
    }
}
