package org.whispersystems.signalservice.internal.push.http;

import com.google.protobuf.ByteString;

import org.signal.protos.resumableuploads.ResumableUploads;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.util.Base64;

import java.io.IOException;

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
    ResumableUploads.ResumableUpload.Builder builder = ResumableUploads.ResumableUpload.newBuilder()
                                                                                       .setSecretKey(ByteString.copyFrom(getSecretKey()))
                                                                                       .setIv(ByteString.copyFrom(getIV()))
                                                                                       .setTimeout(getExpirationTimestamp())
                                                                                       .setCdnNumber(getCdnNumber())
                                                                                       .setCdnKey(getCdnKey())
                                                                                       .setLocation(getResumeLocation())
                                                                                       .setTimeout(getExpirationTimestamp());

    return Base64.encodeBytes(builder.build().toByteArray());
  }

  public static ResumableUploadSpec deserialize(String serializedSpec) throws ResumeLocationInvalidException {
    if (serializedSpec == null) return null;

    try {
      ResumableUploads.ResumableUpload resumableUpload = ResumableUploads.ResumableUpload.parseFrom(ByteString.copyFrom(Base64.decode(serializedSpec)));

      return new ResumableUploadSpec(
          resumableUpload.getSecretKey().toByteArray(),
          resumableUpload.getIv().toByteArray(),
          resumableUpload.getCdnKey(),
          resumableUpload.getCdnNumber(),
          resumableUpload.getLocation(),
          resumableUpload.getTimeout()
      );
    } catch (IOException e) {
      throw new ResumeLocationInvalidException();
    }
  }
}
