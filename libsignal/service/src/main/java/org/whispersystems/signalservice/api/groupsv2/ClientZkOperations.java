package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

/**
 * Contains access to all ZK group operations for the client.
 * <p>
 * Authorization and profile operations.
 */
public final class ClientZkOperations {

  private final ClientZkAuthOperations    clientZkAuthOperations;
  private final ClientZkProfileOperations clientZkProfileOperations;
  private final ServerPublicParams        serverPublicParams;

  public ClientZkOperations(ServerPublicParams serverPublicParams) {
    this.serverPublicParams        = serverPublicParams;
    this.clientZkAuthOperations    = new ClientZkAuthOperations   (serverPublicParams);
    this.clientZkProfileOperations = new ClientZkProfileOperations(serverPublicParams);
  }

  public static ClientZkOperations create(SignalServiceConfiguration configuration) {
    return new ClientZkOperations(new ServerPublicParams(configuration.getZkGroupServerPublicParams()));
  }

  public ClientZkAuthOperations getAuthOperations() {
    return clientZkAuthOperations;
  }

  public ClientZkProfileOperations getProfileOperations() {
    return clientZkProfileOperations;
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }
}
