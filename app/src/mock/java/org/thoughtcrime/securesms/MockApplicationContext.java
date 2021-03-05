package org.thoughtcrime.securesms;

import java.io.File;
import java.io.IOException;

public class MockApplicationContext extends ApplicationContext {

  @Override
  public void onCreate() {
    super.onCreate();

    try {
      MockAppDataInitializer.initialize(this, new File(getExternalFilesDir(null), "mock-data"));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to initialize mock data!", e);
    }
  }
}
