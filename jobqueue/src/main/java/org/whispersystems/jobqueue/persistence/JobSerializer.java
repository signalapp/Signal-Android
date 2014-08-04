package org.whispersystems.jobqueue.persistence;

import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.Job;

import java.io.IOException;

public interface JobSerializer {

  public String serialize(Job job) throws IOException;
  public Job deserialize(EncryptionKeys keys, boolean encrypted, String serialized) throws IOException;

}
