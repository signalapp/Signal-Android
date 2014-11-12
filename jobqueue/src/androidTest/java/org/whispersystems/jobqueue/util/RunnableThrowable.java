package org.whispersystems.jobqueue.util;

public interface RunnableThrowable {

  public void run() throws Exception;

  public void shouldThrow(Boolean value);
}
