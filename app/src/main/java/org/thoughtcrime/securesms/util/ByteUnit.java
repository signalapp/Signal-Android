package org.thoughtcrime.securesms.util;

/**
 * Just like {@link java.util.concurrent.TimeUnit}, but for bytes.
 */
public enum ByteUnit {

  BYTES {
    public long toBytes(long d)     { return d; }
    public long toKilobytes(long d) { return d/1024; }
    public long toMegabytes(long d) { return toKilobytes(d)/1024; }
    public long toGigabytes(long d) { return toMegabytes(d)/1024; }
  },

  KILOBYTES {
    public long toBytes(long d)     { return d * 1024; }
    public long toKilobytes(long d) { return d; }
    public long toMegabytes(long d) { return d/1024; }
    public long toGigabytes(long d) { return toMegabytes(d)/1024; }
  },

  MEGABYTES {
    public long toBytes(long d)     { return toKilobytes(d) * 1024; }
    public long toKilobytes(long d) { return d * 1024; }
    public long toMegabytes(long d) { return d; }
    public long toGigabytes(long d) { return d/1024; }
  },

  GIGABYTES {
    public long toBytes(long d)     { return toKilobytes(d) * 1024; }
    public long toKilobytes(long d) { return toMegabytes(d) * 1024; }
    public long toMegabytes(long d) { return d * 1024; }
    public long toGigabytes(long d) { return d; }
  };

  public long toBytes(long d) { throw new AbstractMethodError(); }
  public long toKilobytes(long d) { throw new AbstractMethodError(); }
  public long toMegabytes(long d) { throw new AbstractMethodError(); }
  public long toGigabytes(long d) { throw new AbstractMethodError(); }
}
