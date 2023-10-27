package org.whispersystems.signalservice.internal.push.http;

import org.signal.protos.resumableuploads.ResumableUpload;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.signal.core.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import okio.ByteString;

public final class ResumableUploadSpec {

  private final byte[] secretKey;
  private final byte[] iv;

  private final String              cdnKey;
  private final Integer             cdnNumber;
  private final String              resumeLocation;
  private final Long                expirationTimestamp;
  private final Map<String, String> headers;

  public ResumableUploadSpec(byte[] secretKey,
                             byte[] iv,
                             String cdnKey,
                             int cdnNumber,
                             String resumeLocation,
                             long expirationTimestamp,
                             Map<String, String> headers)
  {
    this.secretKey           = secretKey;
    this.iv                  = iv;
    this.cdnKey              = cdnKey;
    this.cdnNumber           = cdnNumber;
    this.resumeLocation      = resumeLocation;
    this.expirationTimestamp = expirationTimestamp;
    this.headers             = headers;
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

  public Map<String, String> getHeaders() {
    return headers;
  }

  public ResumableUpload toProto() {
    ResumableUpload.Builder builder = new ResumableUpload.Builder()
        .secretKey(ByteString.of(getSecretKey()))
        .iv(ByteString.of(getIV()))
        .timeout(getExpirationTimestamp())
        .cdnNumber(getCdnNumber())
        .cdnKey(getCdnKey())
        .location(getResumeLocation())
        .timeout(getExpirationTimestamp());

    builder.headers(
        headers.entrySet()
               .stream()
               .map(e -> new ResumableUpload.Header.Builder().key(e.getKey()).value_(e.getValue()).build())
               .collect(Collectors.toList())
    );

    return builder.build();
  }

  public String serialize() {
    return Base64.encodeWithPadding(toProto().encode());
  }

  public static ResumableUploadSpec deserialize(String serializedSpec) throws ResumeLocationInvalidException {
    try {
      ResumableUpload resumableUpload = ResumableUpload.ADAPTER.decode(Base64.decode(serializedSpec));
      return from(resumableUpload);
    } catch (IOException e) {
      throw new ResumeLocationInvalidException();
    }
  }

  public static ResumableUploadSpec from(ResumableUpload resumableUpload) throws ResumeLocationInvalidException {
    if (resumableUpload == null) return null;

    Map<String, String> headers = new HashMap<>();
    for (ResumableUpload.Header header : resumableUpload.headers) {
      headers.put(header.key, header.value_);
    }

    return new ResumableUploadSpec(
        resumableUpload.secretKey.toByteArray(),
        resumableUpload.iv.toByteArray(),
        resumableUpload.cdnKey,
        resumableUpload.cdnNumber,
        resumableUpload.location,
        resumableUpload.timeout,
        headers
    );
  }
}
