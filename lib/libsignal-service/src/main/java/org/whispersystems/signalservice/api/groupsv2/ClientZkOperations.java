package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.ServerPublicParams;
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

/**
 * Contains access to all ZK group operations for the client.
 * <p>
 * Authorization and profile operations.
 */
public final class ClientZkOperations {

  private final ClientZkAuthOperations    clientZkAuthOperations;
  private final ClientZkProfileOperations clientZkProfileOperations;
  private final ClientZkReceiptOperations clientZkReceiptOperations;
  private final ServerPublicParams        serverPublicParams;

  public ClientZkOperations(ServerPublicParams serverPublicParams) {
    this.serverPublicParams        = serverPublicParams;
    this.clientZkAuthOperations    = new ClientZkAuthOperations   (serverPublicParams);
    this.clientZkProfileOperations = new ClientZkProfileOperations(serverPublicParams);
    this.clientZkReceiptOperations = new ClientZkReceiptOperations(serverPublicParams);
  }

  public static ClientZkOperations create(SignalServiceConfiguration configuration) {
    try {
      return new ClientZkOperations(new ServerPublicParams(configuration.getZkGroupServerPublicParams()));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  public ClientZkAuthOperations getAuthOperations() {
    return clientZkAuthOperations;
  }

  public ClientZkProfileOperations getProfileOperations() {
    return clientZkProfileOperations;
  }

  public ClientZkReceiptOperations getReceiptOperations() {
    return clientZkReceiptOperations;
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }
}
