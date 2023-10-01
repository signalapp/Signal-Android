package org.whispersystems.signalservice.api.messages.calls;


import org.whispersystems.signalservice.internal.push.CallMessage;

public class HangupMessage {

  private final long    id;
  private final Type    type;
  private final int     deviceId;

  public HangupMessage(long id, Type type, int deviceId) {
    this.id       = id;
    this.type     = type;
    this.deviceId = deviceId;
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

  public enum Type {
    NORMAL("normal", CallMessage.Hangup.Type.HANGUP_NORMAL),
    ACCEPTED("accepted", CallMessage.Hangup.Type.HANGUP_ACCEPTED),
    DECLINED("declined", CallMessage.Hangup.Type.HANGUP_DECLINED),
    BUSY("busy", CallMessage.Hangup.Type.HANGUP_BUSY),
    NEED_PERMISSION("need_permission", CallMessage.Hangup.Type.HANGUP_NEED_PERMISSION);

    private final String code;
    private final CallMessage.Hangup.Type protoType;

    Type(String code, CallMessage.Hangup.Type protoType) {
      this.code      = code;
      this.protoType = protoType;
    }

    public String getCode() {
      return code;
    }

    public CallMessage.Hangup.Type getProtoType() {
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

    public static Type fromProto(CallMessage.Hangup.Type hangupType) {
      for (Type type : Type.values()) {
        if (type.getProtoType().equals(hangupType)) {
          return type;
        }
      }

      throw new IllegalArgumentException("Unexpected type: " + hangupType.name());
    }
  }
}
