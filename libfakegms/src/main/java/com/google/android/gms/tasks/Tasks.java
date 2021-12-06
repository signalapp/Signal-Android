package com.google.android.gms.tasks;

import java.util.concurrent.ExecutionException;

public final class Tasks {
  public static <TResult> TResult await(Task<TResult> task) throws ExecutionException, InterruptedException {
    return task.getResult();
  }
}
