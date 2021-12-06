package com.google.firebase.installations;

import com.google.android.gms.tasks.Task;

public class FirebaseInstallations {
  private static final FirebaseInstallations INSTANCE = new FirebaseInstallations();

  public static FirebaseInstallations getInstance() {
    return INSTANCE;
  }

  public Task<Void> delete() {
    return new Task<>();
  }
}
