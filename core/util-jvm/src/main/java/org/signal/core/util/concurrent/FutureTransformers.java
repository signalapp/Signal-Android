/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.concurrent;

public final class FutureTransformers {

  public static <Input, Output> ListenableFuture<Output> map(ListenableFuture<Input> future, Transformer<Input, Output> transformer) {
    return new FutureMapTransformer<>(future, transformer);
  }

  public interface Transformer<Input, Output> {
    Output transform(Input a) throws Exception;
  }
}
