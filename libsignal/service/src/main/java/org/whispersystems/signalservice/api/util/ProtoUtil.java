package org.whispersystems.signalservice.api.util;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSetLite;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

public final class ProtoUtil {

  private static final String TAG = ProtoUtil.class.getSimpleName();

  private static final String DEFAULT_INSTANCE = "DEFAULT_INSTANCE";

  private ProtoUtil() { }

  /**
   * True if there are unknown fields anywhere inside the proto or its nested protos.
   */
  @SuppressWarnings("rawtypes")
  public static boolean hasUnknownFields(GeneratedMessageLite rootProto) {
    try {
      List<GeneratedMessageLite> allProtos = getInnerProtos(rootProto);
      allProtos.add(rootProto);

      for (GeneratedMessageLite proto : allProtos) {
        Field field = GeneratedMessageLite.class.getDeclaredField("unknownFields");
        field.setAccessible(true);

        UnknownFieldSetLite unknownFields = (UnknownFieldSetLite) field.get(proto);

        if (unknownFields != null && unknownFields.getSerializedSize() > 0) {
          return true;
        }
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      Log.w(TAG, "Failed to read proto private fields! Assuming no unknown fields.");
    }

    return false;
  }

  /**
   * This takes two arguments: A proto model, and the bytes of another proto model of the same type.
   * This will take the proto model and append onto it any unknown fields from the serialized proto
   * model. Why is this useful? Well, if you do {@code myProto.parseFrom(data).toBuilder().build()},
   * you will lose any unknown fields that were in {@code data}. This lets you create a new model
   * and plop the unknown fields back on from some other instance.
   *
   * A notable limitation of the current implementation is, however, that it does not support adding
   * back unknown fields to *inner* messages. Unknown fields on inner messages will simply not be
   * acknowledged.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <Proto extends GeneratedMessageLite> Proto combineWithUnknownFields(Proto proto, byte[] serializedWithUnknownFields) {
    if (serializedWithUnknownFields == null) {
      return proto;
    }

    try {
      Proto  protoWithUnknownFields = (Proto) proto.getParserForType().parseFrom(serializedWithUnknownFields);
      byte[] unknownFields          = getUnknownFields(protoWithUnknownFields);

      if (unknownFields == null) {
        return proto;
      }

      byte[] combined = ByteUtil.combine(proto.toByteArray(), unknownFields);

      return (Proto) proto.getParserForType().parseFrom(combined);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException();
    }
  }

  @SuppressWarnings("rawtypes")
  private static byte[] getUnknownFields(GeneratedMessageLite proto) {
    try {
      Field field = GeneratedMessageLite.class.getDeclaredField("unknownFields");
      field.setAccessible(true);
      UnknownFieldSetLite unknownFields = (UnknownFieldSetLite) field.get(proto);

      if (unknownFields == null || unknownFields.getSerializedSize() == 0) {
        return null;
      }

      ByteArrayOutputStream byteStream   = new ByteArrayOutputStream();
      CodedOutputStream     outputStream = CodedOutputStream.newInstance(byteStream);

      unknownFields.writeTo(outputStream);
      outputStream.flush();

      return byteStream.toByteArray();
    } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
      Log.w(TAG, "Failed to retrieve unknown fields.", e);
      return null;
    }
  }

  /**
   * Recursively retrieves all inner complex proto types inside a given proto.
   */
  @SuppressWarnings("rawtypes")
  private static List<GeneratedMessageLite> getInnerProtos(GeneratedMessageLite proto) {
    List<GeneratedMessageLite> innerProtos = new LinkedList<>();

    try {
      Field[] fields = proto.getClass().getDeclaredFields();

      for (Field field : fields) {
        if (!field.getName().equals(DEFAULT_INSTANCE) && GeneratedMessageLite.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);

          GeneratedMessageLite inner = (GeneratedMessageLite) field.get(proto);
          innerProtos.add(inner);
          innerProtos.addAll(getInnerProtos(inner));
        }
      }

    } catch (IllegalAccessException e) {
      Log.w(TAG, "Failed to get inner protos!", e);
    }

    return innerProtos;
  }
}
