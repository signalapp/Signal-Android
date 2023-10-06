package org.whispersystems.signalservice.internal.util.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lets you perform a simple transform on the result of a future that maps it to a different value.
 */
class FutureMapTransformer<Input, Output> implements ListenableFuture<Output> {

  private final ListenableFuture<Input>                       future;
  private final FutureTransformers.Transformer<Input, Output> transformer;

  FutureMapTransformer(ListenableFuture<Input> future, FutureTransformers.Transformer<Input, Output> transformer) {
    this.future      = future;
    this.transformer = transformer;
  }

  @Override
  public void addListener(Listener<Output> listener) {
    future.addListener(new Listener<Input>() {
      @Override
      public void onSuccess(Input result) {
        try {
          listener.onSuccess(transformer.transform(result));
        } catch (Exception e) {
          listener.onFailure(new ExecutionException(e));
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        listener.onFailure(e);
      }
    });
  }

  @Override
  public boolean cancel(boolean b) {
    return future.cancel(b);
  }

  @Override
  public boolean isCancelled() {
    return future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return future.isDone();
  }

  @Override
  public Output get() throws InterruptedException, ExecutionException {
    Input input = future.get();
    try {
      return transformer.transform(input);
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public Output get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
    Input input = future.get(l, timeUnit);
    try {
      return transformer.transform(input);
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }
}
