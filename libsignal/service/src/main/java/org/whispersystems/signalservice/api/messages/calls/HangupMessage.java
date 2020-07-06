package org.whispersystems.signalservice.api.messages.calls;


import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class HangupMessage {

  private final long    id;
  private final Type    type;
  private final int     deviceId;
  private final boolean isLegacy;

  public HangupMessage(long id, Type type, int deviceId, boolean isLegacy) {
    this.id       = id;
    this.type     = type;
    this.deviceId = deviceId;
    this.isLegacy = isLegacy;
  }

  public long getId() {
    return id;
  }

  public Type getType() {
    return type;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public boolean isLegacy() {
    return isLegacy;
  }

  public enum Type {
    NORMAL("normal", SignalServiceProtos.CallMessage.Hangup.Type.HANGUP_NORMAL),
    ACCEPTED("accepted", SignalServiceProtos.CallMessage.Hangup.Type.HANGUP_ACCEPTED),
    DECLINED("declined", SignalServiceProtos.CallMessage.Hangup.Type.HANGUP_DECLINED),
    BUSY("busy", SignalServiceProtos.CallMessage.Hangup.Type.HANGUP_BUSY),
    NEED_PERMISSION("need_permission", SignalServiceProtos.CallMessage.Hangup.Type.HANGUP_NEED_PERMISSION);

    private final String code;
    private final SignalServiceProtos.CallMessage.Hangup.Type protoType;

    Type(String code, SignalServiceProtos.CallMessage.Hangup.Type protoType) {
      this.code      = code;
      this.protoType = protoType;
    }

    public String getCode() {
      return code;
    }

    public SignalServiceProtos.CallMessage.Hangup.Type getProtoType() {
      return protoType;
    }

    public static Type fromCode(String code) {
      for (Type type : Type.values()) {
        if (type.getCode().equals(code)) {
          return type;
        }
      }

      throw new IllegalArgumentException("Unexpected code: " + code);
    }

    public static Type fromProto(SignalServiceProtos.CallMessage.Hangup.Type hangupType) {
      for (Type type : Type.values()) {
        if (type.getProtoType().equals(hangupType)) {
          return type;
        }
      }

      throw new IllegalArgumentException("Unexpected type: " + hangupType.name());
    }
  }
}
