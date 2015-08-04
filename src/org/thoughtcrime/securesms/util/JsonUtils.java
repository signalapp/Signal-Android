package org.thoughtcrime.securesms.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public static <T> T fromJson(String serialized, Class<T> clazz) throws IOException {
    return objectMapper.readValue(serialized, clazz);
  }

  public static <T> T fromJson(InputStreamReader serialized, Class<T> clazz) throws IOException {
    return objectMapper.readValue(serialized, clazz);
  }

  public static String toJson(Object object) throws IOException {
    return objectMapper.writeValueAsString(object);
  }

  public static ObjectMapper getMapper() {
    return objectMapper;
  }
}
