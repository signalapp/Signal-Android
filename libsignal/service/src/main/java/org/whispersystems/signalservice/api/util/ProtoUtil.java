package org.whispersystems.signalservice.api.util;

import com.squareup.wire.Message;

import org.signal.libsignal.protocol.logging.Log;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import okio.ByteString;

public final class ProtoUtil {

  private static final String TAG = ProtoUtil.class.getSimpleName();

  private static final String DEFAULT_INSTANCE = "DEFAULT_INSTANCE";

  private ProtoUtil() {}

  /**
   * True if there are unknown fields anywhere inside the proto or its nested protos.
   */
  @SuppressWarnings("rawtypes")
  public static boolean hasUnknownFields(Message rootProto) {
    List<Message> allProtos = getInnerProtos(rootProto);
    allProtos.add(rootProto);

    for (Message proto : allProtos) {
      ByteString unknownFields = proto.unknownFields();

      if (unknownFields.size() > 0) {
        return true;
      }
    }

    return false;
  }

  /**
   * Recursively retrieves all inner complex proto types inside a given proto.
   */
  @SuppressWarnings("rawtypes")
  private static List<Message> getInnerProtos(Message proto) {
    List<Message> innerProtos = new LinkedList<>();

    try {
      Field[] fields = proto.getClass().getDeclaredFields();

      for (Field field : fields) {
        if (!field.getName().equals(DEFAULT_INSTANCE) && Message.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);

          Message inner = (Message) field.get(proto);
          if (inner != null) {
            innerProtos.add(inner);
            innerProtos.addAll(getInnerProtos(inner));
          }
        }
      }

    } catch (IllegalAccessException e) {
      Log.w(TAG, "Failed to get inner protos!", e);
    }

    return innerProtos;
  }
}
