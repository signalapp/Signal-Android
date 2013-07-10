package org.whispersystems.textsecure.directory;

public class DirectoryDescriptor {
  private String version;
  private long capacity;
  private int hashCount;

  public String getUrl() {
    return url;
  }

  public int getHashCount() {
    return hashCount;
  }

  public long getCapacity() {
    return capacity;
  }

  public String getVersion() {
    return version;
  }

  private String url;

}
