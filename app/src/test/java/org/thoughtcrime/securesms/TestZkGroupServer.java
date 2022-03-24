package org.thoughtcrime.securesms;

import org.signal.libsignal.zkgroup.ServerPublicParams;
import org.signal.libsignal.zkgroup.ServerSecretParams;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupPublicParams;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.signal.libsignal.zkgroup.profiles.ServerZkProfileOperations;
import org.whispersystems.signalservice.test.LibSignalLibraryUtil;

import java.util.UUID;

/**
 * Provides Zk group operations that the server would provide.
 * Copied in app from libsignal
 */
public final class TestZkGroupServer {

  private final ServerPublicParams        serverPublicParams;
  private final ServerZkProfileOperations serverZkProfileOperations;

  public TestZkGroupServer() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS();

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
