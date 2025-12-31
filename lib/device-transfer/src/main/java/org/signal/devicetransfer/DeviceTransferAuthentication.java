package org.signal.devicetransfer;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Allows two parties to authenticate each other via short authentication strings (SAS).
 * <ol>
 * <li>Client generates a random data, and then MAC(k=random data, m=certificate) to get a commitment.</li>
 * <li>Client sends commitment to the server.</li>
 * <li>Server stores commitment and generates it's own random data.</li>
 * <li>Server sends it's random data to client.</li>
 * <li>Client stores server random data and sends it's random data to the server.</li>
 * <li>Server can then MAC(k=client random data, m=certificate) to verify the original commitment.</li>
 * <li>Client and Server can compute a SAS using the two randoms.</li>
 * </ol>
 */
final class DeviceTransferAuthentication {

  public static final  int    DIGEST_LENGTH = 32;
  private static final String MAC_ALGORITHM = "HmacSHA256";

  private DeviceTransferAuthentication() {}

  /**
   * Perform the client side of the SAS generation via input and output streams.
   *
   * @param certificate  x509 certificate of the TLS connection
   * @param inputStream  stream to read data from the {@link Server}
   * @param outputStream stream to write data to the {@link Server}
   * @return Computed SAS
   * @throws DeviceTransferAuthenticationException When something in the code generation fails
   * @throws IOException                           When a communication issue occurs over one of the two streams
   */
  public static int generateClientAuthenticationCode(@NonNull byte[] certificate,
                                                     @NonNull InputStream inputStream,
                                                     @NonNull OutputStream outputStream)
      throws DeviceTransferAuthenticationException, IOException
  {
    Client authentication = new Client(certificate);
    outputStream.write(authentication.getCommitment());
    outputStream.flush();

    byte[] serverRandom = new byte[DeviceTransferAuthentication.DIGEST_LENGTH];
    StreamUtil.readFully(inputStream, serverRandom, serverRandom.length);

    byte[] clientRandom = authentication.setServerRandomAndGetClientRandom(serverRandom);
    outputStream.write(clientRandom);
    outputStream.flush();

    return authentication.computeShortAuthenticationCode();
  }

  /**
   * Perform the server side of the SAS generation via input and output streams.
   *
   * @param certificate  x509 certificate of the TLS connection
   * @param inputStream  stream to read data from the {@link Client}
   * @param outputStream stream to write data to the {@link Client}
   * @return Computed SAS
   * @throws DeviceTransferAuthenticationException When something in the code generation fails or the client
   *                                               provided commitment does not match the computed version
   * @throws IOException                           When a communication issue occurs over one of the two streams
   */
  public static int generateServerAuthenticationCode(@NonNull byte[] certificate,
                                                     @NonNull InputStream inputStream,
                                                     @NonNull OutputStream outputStream)
      throws DeviceTransferAuthenticationException, IOException
  {
    byte[] clientCommitment = new byte[DeviceTransferAuthentication.DIGEST_LENGTH];
    StreamUtil.readFully(inputStream, clientCommitment, clientCommitment.length);

    DeviceTransferAuthentication.Server authentication = new DeviceTransferAuthentication.Server(certificate, clientCommitment);

    outputStream.write(authentication.getRandom());
    outputStream.flush();

    byte[] clientRandom = new byte[DeviceTransferAuthentication.DIGEST_LENGTH];
    StreamUtil.readFully(inputStream, clientRandom, clientRandom.length);
    authentication.setClientRandom(clientRandom);

    return authentication.computeShortAuthenticationCode();
  }

  private static @NonNull Mac getMac(@NonNull byte[] secret) throws DeviceTransferAuthenticationException {
    try {
      Mac mac = Mac.getInstance(MAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, MAC_ALGORITHM));
      return mac;
    } catch (Exception e) {
      throw new DeviceTransferAuthenticationException(e);
    }
  }

  private static int computeShortAuthenticationCode(@NonNull byte[] clientRandom,
                                                    @NonNull byte[] serverRandom)
      throws DeviceTransferAuthenticationException
  {
    byte[] authentication = getMac(clientRandom).doFinal(serverRandom);

    ByteBuffer buffer = ByteBuffer.wrap(authentication);
    buffer.order(ByteOrder.BIG_ENDIAN);
    return buffer.getInt(authentication.length - 4) & 0x007f_ffff;
  }

  private static @NonNull byte[] copyOf(@NonNull byte[] input) {
    return Arrays.copyOf(input, input.length);
  }

  private static void validateLength(@NonNull byte[] input) throws DeviceTransferAuthenticationException {
    if (input.length != DIGEST_LENGTH) {
      throw new DeviceTransferAuthenticationException("invalid digest length");
    }
  }

  /**
   * Server side of authentication, responsible for verifying connecting
   * devices commitment and generating a code.
   */
  @VisibleForTesting
  static final class Server {
    private final byte[] random;
    private final byte[] certificate;
    private final byte[] clientCommitment;
    private       byte[] clientRandom;

    public Server(@NonNull byte[] certificate, @NonNull byte[] clientCommitment) throws DeviceTransferAuthenticationException {
      validateLength(clientCommitment);

      this.certificate      = copyOf(certificate);
      this.clientCommitment = copyOf(clientCommitment);

      SecureRandom secureRandom = new SecureRandom();

      this.random = new byte[DIGEST_LENGTH];
      secureRandom.nextBytes(this.random);
    }

    public @NonNull byte[] getRandom() {
      return copyOf(random);
    }

    public void setClientRandom(@NonNull byte[] clientRandom) throws DeviceTransferAuthenticationException {
      validateLength(clientRandom);
      this.clientRandom = copyOf(clientRandom);
    }

    public void verifyClientRandom() throws DeviceTransferAuthenticationException {
      if (clientRandom == null) {
        throw new DeviceTransferAuthenticationException("no client random set");
      }

      byte[]  computedCommitment = getMac(copyOf(clientRandom)).doFinal(copyOf(certificate));
      boolean commitmentsMatch   = MessageDigest.isEqual(clientCommitment, computedCommitment);
      if (!commitmentsMatch) {
        throw new DeviceTransferAuthenticationException("commitments do not match, do not proceed");
      }
    }

    public int computeShortAuthenticationCode() throws DeviceTransferAuthenticationException {
      verifyClientRandom();
      return DeviceTransferAuthentication.computeShortAuthenticationCode(copyOf(clientRandom), copyOf(random));
    }
  }

  /**
   * Client side of authentication, responsible for starting authentication with server.
   */
  @VisibleForTesting
  static final class Client {

    private final byte[] random;
    private final byte[] commitment;
    private       byte[] serverRandom;

    public Client(@NonNull byte[] certificate) throws DeviceTransferAuthenticationException {
      SecureRandom secureRandom = new SecureRandom();

      this.random = new byte[DIGEST_LENGTH];
      secureRandom.nextBytes(this.random);

      commitment = getMac(copyOf(this.random)).doFinal(copyOf(certificate));
    }

    public @NonNull byte[] getCommitment() {
      return copyOf(commitment);
    }

    public @NonNull byte[] setServerRandomAndGetClientRandom(@NonNull byte[] serverRandom) throws DeviceTransferAuthenticationException {
      validateLength(serverRandom);
      this.serverRandom = copyOf(serverRandom);
      return copyOf(random);
    }

    public int computeShortAuthenticationCode() throws DeviceTransferAuthenticationException {
      if (serverRandom == null) {
        throw new DeviceTransferAuthenticationException("no server random set");
      }
      return DeviceTransferAuthentication.computeShortAuthenticationCode(copyOf(random), copyOf(serverRandom));
    }
  }

  public static final class DeviceTransferAuthenticationException extends Exception {
    public DeviceTransferAuthenticationException(@NonNull String message) {
      super(message);
    }

    public DeviceTransferAuthenticationException(@NonNull Throwable cause) {
      super(cause);
    }
  }
}
