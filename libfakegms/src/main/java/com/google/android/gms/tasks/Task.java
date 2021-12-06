package com.google.android.gms.tasks;

public class Task<TResult> {
  public Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> listener) {
    listener.onComplete(this);
    return this;
  }

  public Task<TResult> addOnFailureListener(OnFailureListener<TResult> listener) {
    listener.onFailure(getException());
    return this;
  }

  public Task<TResult> addOnSuccessListener(OnSuccessListener<TResult> listener) {
    return this;
  }

  public TResult getResult() {
    return null;
  }

  public <X extends Throwable> TResult getResult(Class<X> exceptionType) {
    return null;
  }

  public boolean isSuccessful() {
    return false;
  }

  public Exception getException() {
    return new UnsupportedOperationException();
  }
}
