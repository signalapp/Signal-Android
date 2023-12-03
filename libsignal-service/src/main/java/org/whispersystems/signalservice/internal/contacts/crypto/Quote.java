package org.whispersystems.signalservice.internal.contacts.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Quote {

  private static final long SGX_FLAGS_INITTED        = 0x0000_0000_0000_0001L;
  private static final long SGX_FLAGS_DEBUG          = 0x0000_0000_0000_0002L;
  private static final long SGX_FLAGS_MODE64BIT      = 0x0000_0000_0000_0004L;
  private static final long SGX_FLAGS_PROVISION_KEY  = 0x0000_0000_0000_0004L;
  private static final long SGX_FLAGS_EINITTOKEN_KEY = 0x0000_0000_0000_0004L;
  private static final long SGX_FLAGS_RESERVED       = 0xFFFF_FFFF_FFFF_FFC8L;
  private static final long SGX_XFRM_LEGACY          = 0x0000_0000_0000_0003L;
  private static final long SGX_XFRM_AVX             = 0x0000_0000_0000_0006L;
  private static final long SGX_XFRM_RESERVED        = 0xFFFF_FFFF_FFFF_FFF8L;

  private final int     version;
  private final boolean isSigLinkable;
  private final long    gid;
  private final int     qeSvn;
  private final int     pceSvn;
  private final byte[]  basename      = new byte[32];
  private final byte[]  cpuSvn        = new byte[16];
  private final long    flags;
  private final long    xfrm;
  private final byte[]  mrenclave     = new byte[32];
  private final byte[]  mrsigner      = new byte[32];
  private final int     isvProdId;
  private final int     isvSvn;
  private final byte[]  reportData    = new byte[64];
  private final byte[]  signature;
  private final byte[]  quoteBytes;

  public Quote(byte[] quoteBytes) throws InvalidQuoteFormatException {
    this.quoteBytes = quoteBytes;

    ByteBuffer quoteBuf = ByteBuffer.wrap(quoteBytes);
    quoteBuf.order(ByteOrder.LITTLE_ENDIAN);

    this.version = quoteBuf.getShort(0) & 0xFFFF;
    if (!(version >= 1 && version <= 2)) {
      throw new InvalidQuoteFormatException("unknown_quote_version "+version);
    }

    int sign_type = quoteBuf.getShort(2) & 0xFFFF;
    if ((sign_type & ~1) != 0) {
      throw new InvalidQuoteFormatException("unknown_quote_sign_type "+sign_type);
    }

    this.isSigLinkable = sign_type == 1;
    this.gid           = quoteBuf.getInt(4)   & 0xFFFF_FFFF;
    this.qeSvn         = quoteBuf.getShort(8) &      0xFFFF;

    if (version > 1) {
      this.pceSvn = quoteBuf.getShort(10) & 0xFFFF;
    } else {
      readZero(quoteBuf, 10, 2);
      this.pceSvn = 0;
    }

    readZero(quoteBuf, 12, 4); // xeid (reserved)
    read(quoteBuf, 16, basename);

    //
    // report_body
    //

    read(quoteBuf, 48, cpuSvn);
    readZero(quoteBuf, 64, 4); // misc_select (reserved)
    readZero(quoteBuf, 68, 28); // reserved1
    this.flags = quoteBuf.getLong(96);
    if ((flags & SGX_FLAGS_RESERVED ) != 0 ||
        (flags & SGX_FLAGS_INITTED  ) == 0 ||
        (flags & SGX_FLAGS_MODE64BIT) == 0) {
      throw new InvalidQuoteFormatException("bad_quote_flags "+flags);
    }
    this.xfrm = quoteBuf.getLong(104);
    if ((xfrm & SGX_XFRM_RESERVED) != 0) {
      throw new InvalidQuoteFormatException("bad_quote_xfrm "+xfrm);
    }
    read(quoteBuf, 112, mrenclave);
    readZero(quoteBuf, 144, 32); // reserved2
    read(quoteBuf, 176, mrsigner);
    readZero(quoteBuf, 208, 96); // reserved3
    this.isvProdId = quoteBuf.getShort(304) & 0xFFFF;
    this.isvSvn    = quoteBuf.getShort(306) & 0xFFFF;
    readZero(quoteBuf, 308, 60); // reserved4
    read(quoteBuf, 368, reportData);

    // quote signature
    int sig_len = quoteBuf.getInt(432) & 0xFFFF_FFFF;
    if (sig_len != quoteBytes.length - 436) {
      throw new InvalidQuoteFormatException("bad_quote_sig_len "+sig_len);
    }
    this.signature = new byte[sig_len];
    read(quoteBuf, 436, signature);
  }

  public byte[] getReportData() {
    return reportData;
  }

  private void read(ByteBuffer quoteBuf, int pos, byte[] buf) {
    quoteBuf.position(pos);
    quoteBuf.get(buf);
  }

  private void readZero(ByteBuffer quoteBuf, int pos, int count) {
    byte[] zeroBuf = new byte[count];
    read(quoteBuf, pos, zeroBuf);
    for (int zeroBufIdx = 0; zeroBufIdx < count; zeroBufIdx++) {
      if (zeroBuf[zeroBufIdx] != 0) {
        throw new IllegalArgumentException("quote_reserved_mismatch "+pos);
      }
    }
  }

  public byte[] getQuoteBytes() {
    return quoteBytes;
  }

  public byte[] getMrenclave() {
    return mrenclave;
  }

  public boolean isDebugQuote() {
    return (flags & SGX_FLAGS_DEBUG) != 0;
  }

  public static class InvalidQuoteFormatException extends Exception {
    public InvalidQuoteFormatException(String value) {
      super(value);
    }
  }
}
