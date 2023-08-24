package org.whispersystems.signalservice.internal.push.http;

import org.signal.protos.resumableuploads.ResumableUpload;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.util.Base64;

import java.io.IOException;

import okio.ByteString;

public final class ResumableUploadSpec {

  private final byte[] secretKey;
  private final byte[] iv;

  private final String  cdnKey;
  private final Integer cdnNumber;
  private final String  resumeLocation;
  private final Long    expirationTimestamp;

  public ResumableUploadSpec(byte[] secretKey,
                             byte[] iv,
                             String cdnKey,
                             int cdnNumber,
                             String resumeLocation,
                             long expirationTimestamp)
  {
    this.secretKey           = secretKey;
    this.iv                  = iv;
    this.cdnKey              = cdnKey;
    this.cdnNumber           = cdnNumber;
    this.resumeLocation      = resumeLocation;
    this.expirationTimestamp = expirationTimestamp;
  }

  public byte[] getSecretKey() {
    return secretKey;
  }

  public byte[] getIV() {
    return iv;
  }

  public String getCdnKey() {
    return cdnKey;
  }

  public Integer getCdnNumber() {
    return cdnNumber;
  }

  public String getResumeLocation() {
    return resumeLocation;
  }

  public Long getExpirationTimestamp() {
    return expirationTimestamp;
  }

  public String serialize() {
    ResumableUpload.Builder builder = new ResumableUpload.Builder()
                                                         .secretKey(ByteString.of(getSecretKey()))
                                                         .iv(ByteString.of(getIV()))
                                                         .timeout(getExpirationTimestamp())
                                                         .cdnNumber(getCdnNumber())
                                                         .cdnKey(getCdnKey())
                                                         .location(getResumeLocation())
                                                         .timeout(getExpirationTimestamp());

    return Base64.encodeBytes(builder.build().encode());
  }

  public static ResumableUploadSpec deserialize(String serializedSpec) throws ResumeLocationInvalidException {
    if (serializedSpec == null) return null;

    try {
      ResumableUpload resumableUpload = ResumableUpload.ADAPTER.decode(Base64.decode(serializedSpec));

      return new ResumableUploadSpec(
          resumableUpload.secretKey.toByteArray(),
          resumableUpload.iv.toByteArray(),
          resumableUpload.cdnKey,
          resumableUpload.cdnNumber,
          resumableUpload.location,
          resumableUpload.timeout
      );
    } catch (IOException e) {
      throw new ResumeLocationInvalidException();
    }
  }
}
