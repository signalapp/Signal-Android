package org.whispersystems.signalservice.internal.groupsv2;

import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;

public final class ClientZkOperations {

  private final ClientZkProfileOperations clientZkProfileOperations;

  public ClientZkOperations(ServerPublicParams serverPublicParams) {
    clientZkProfileOperations = new ClientZkProfileOperations(serverPublicParams);
  }

  public ClientZkProfileOperations getProfileOperations() {
    return clientZkProfileOperations;
  }
}
