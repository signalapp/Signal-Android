package org.signal.zkgroup.profiles;

import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.VerificationFailedException;

import java.security.SecureRandom;
import java.util.UUID;

public final class ClientZkProfileOperations {
  public ClientZkProfileOperations(ServerPublicParams serverPublicParams) {
  }

  public ProfileKeyCredentialRequestContext createProfileKeyCredentialRequestContext(SecureRandom random, UUID target, ProfileKey profileKey) {
    throw new AssertionError();
  }

  public ProfileKeyCredential receiveProfileKeyCredential(ProfileKeyCredentialRequestContext requestContext, ProfileKeyCredentialResponse profileKeyCredentialResponse) throws VerificationFailedException {
    throw new AssertionError();
  }
}
