package org.thoughtcrime.securesms.sms;


import java.util.HashMap;

public class MultipartSmsIdentifier {

  private static final MultipartSmsIdentifier instance = new MultipartSmsIdentifier();

  public static MultipartSmsIdentifier getInstance() {
    return instance;
  }

  private final HashMap<String, Integer>  idMap = new HashMap<String, Integer>();

  public synchronized byte getIdForRecipient(String recipient) {
    Integer currentId;

    if (idMap.containsKey(recipient)) {
      currentId = idMap.get(recipient);
      idMap.remove(recipient);
    } else {
      currentId = 0;
    }

    byte id  = currentId.byteValue();
    idMap.put(recipient, (currentId + 1) % 255);

    return id;
  }

}
