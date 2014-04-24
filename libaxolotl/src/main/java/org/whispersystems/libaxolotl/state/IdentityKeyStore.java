package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;

/**
 * Provides an interface to identity information.
 *
 * @author Moxie Marlinspike
 */
public interface IdentityKeyStore {

  /**
   * Get the local client's identity key pair.
   *
   * @return The local client's persistent identity key pair.
   */
  public IdentityKeyPair getIdentityKeyPair();

  /**
   * Return the local client's registration ID.
   * <p>
   * Clients should maintain a registration ID, a random number
   * between 1 and 16380 that's generated once at install time.
   *
   * @return the local client's registration ID.
   */
  public int             getLocalRegistrationId();

  /**
   * Save a remote client's identity key
   * <p>
   * Store a remote client's identity key as trusted.
   *
   * @param recipientId The recipient ID of the remote client.
   * @param identityKey The remote client's identity key.
   */
  public void            saveIdentity(long recipientId, IdentityKey identityKey);

}
