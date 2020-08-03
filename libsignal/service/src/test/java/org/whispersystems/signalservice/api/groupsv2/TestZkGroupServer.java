package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.ServerSecretParams;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupPublicParams;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;
import org.whispersystems.signalservice.testutil.ZkGroupLibraryUtil;

import java.util.UUID;

/**
 * Provides Zk group operations that the server would provide.
 */
final class TestZkGroupServer {

  private final ServerPublicParams        serverPublicParams;
  private final ServerZkProfileOperations serverZkProfileOperations;

  TestZkGroupServer() {
    ZkGroupLibraryUtil.assumeZkGroupSupportedOnOS();

    ServerSecretParams serverSecretParams = ServerSecretParams.generate();

    serverPublicParams        = serverSecretParams.getPublicParams();
    serverZkProfileOperations = new ServerZkProfileOperations(serverSecretParams);
  }

  public ServerPublicParams getServerPublicParams() {
    return serverPublicParams;
  }

  public ProfileKeyCredentialResponse getProfileKeyCredentialResponse(ProfileKeyCredentialRequest request, UUID uuid, ProfileKeyCommitment commitment) throws VerificationFailedException {
     return serverZkProfileOperations.issueProfileKeyCredential(request, uuid, commitment);
  }

  public void assertProfileKeyCredentialPresentation(GroupPublicParams publicParams, ProfileKeyCredentialPresentation profileKeyCredentialPresentation) {
    try {
      serverZkProfileOperations.verifyProfileKeyCredentialPresentation(publicParams, profileKeyCredentialPresentation);
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }
  }
}
