package org.whispersystems.jobqueue.util;

public interface RunnableThrowable {
  public Boolean shouldThrow = false;

  public void run() throws Throwable;

  public void shouldThrow(Boolean value);
}
