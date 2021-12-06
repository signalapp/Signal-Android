package com.google.firebase.iid;

import com.google.android.gms.tasks.Task;

public class FirebaseInstanceId {
  private static final FirebaseInstanceId INSTANCE = new FirebaseInstanceId();

  public static FirebaseInstanceId getInstance() {
    return INSTANCE;
  }

  public void deleteInstanceId() {
  }

  public Task getInstanceId() {
    return new Task();
  }
}
