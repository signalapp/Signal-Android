package com.google.firebase.messaging;

import com.google.android.gms.tasks.Task;

public class FirebaseMessaging {
  private static final FirebaseMessaging INSTANCE = new FirebaseMessaging();

  public static FirebaseMessaging getInstance() {
    return INSTANCE;
  }

  public Task<String> getToken() {
    return new Task<>();
  }
}
