package com.google.android.gms.tasks;

public interface OnCompleteListener<TResult> {
  void onComplete(Task<TResult> result);
}
