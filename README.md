# libtextsecure-java

A Java library for communicating via TextSecure.

## Implementing the Axolotl interfaces

The axolotl encryption protocol is a stateful protocol, so libtextsecure users
need to implement the storage interface `AxolotlStore`, which handles load/store
of your key and session information to durable media.

## Creating keys

`````
IdentityKeyPair    identityKey        = KeyHelper.generateIdentityKeyPair();
List<PreKeyRecord> oneTimePreKeys     = KeyHelper.generatePreKeys(100);
PreKeyRecord       lastResortKey      = KeyHelper.generateLastResortKey();
SignedPreKeyRecord signedPreKeyRecord = KeyHelper.generateSignedPreKey(identityKey, signedPreKeyId);
`````

The above are then stored locally so that they're available for load via the `AxolotlStore`.

## Registering

At install time, clients need to register with the TextSecure server.

`````
private final String     URL         = "https://my.textsecure.server.com";
private final TrustStore TRUST_STORE = new MyTrustStoreImpl();
private final String     USERNAME    = "+14151231234";
private final String     PASSWORD    = generateRandomPassword();

TextSecureAccountManager accountManager = new TextSecureAccountManager(URL, TRUST_STORE,
                                                                       USERNAME, PASSWORD);

accountManager.requestSmsVerificationCode();
accountManager.verifyAccount(receivedSmsVerificationCode, generateRandomSignalingKey(),
                             false, generateRandomInstallId());
accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
accountManager.setPreKeys(identityKey.getPublic(), lastResortKey, signedPreKey, oneTimePreKeys);
`````

## Sending text messages

`````
TextSecureMessageSender messageSender = new TextSecureMessageSender(URL, TRUST_STORE, USERNAME, PASSWORD,
                                                                    localRecipientId, new MyAxolotlStore(),
                                                                    Optional.absent());

messageSender.sendMessage(new TextSecureAddress("+14159998888"),
                          TextSecureMessage.newBuilder()
                                           .withBody("Hello, world!")
                                           .build());
`````

## Sending media messages

`````
TextSecureMessageSender messageSender = new TextSecureMessageSender(URL, TRUST_STORE, USERNAME, PASSWORD,
                                                                    localRecipientId, new MyAxolotlStore(),
                                                                    Optional.absent());

File                 myAttachment     = new File("/path/to/my.attachment");
FileInputStream      attachmentStream = new FileInputStream(myAttachment);
TextSecureAttachment attachment       = TextSecureAttachment.newStreamBuilder()
                                                            .withStream(attachmentStream)
                                                            .withContentType("image/png")
                                                            .withLength(myAttachment.size())
                                                            .build();

messageSender.sendMessage(new TextSecureAddress("+14159998888"),
                          TextSecureMessage.newBuilder()
                                           .withBody("An attachment!")
                                           .withAttachment(attachment)
                                           .build());

`````

## Receiving messages

`````
TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, USERNAME, PASSWORD, mySignalingKey);
TextSecureMessagePipe     messagePipe;

try {
  messagePipe = messageReciever.createMessagePipe();

  while (listeningForMessages) {
    TextSecureEnvelope envelope = messagePipe.read(timeout, timeoutTimeUnit);
    TextSecureCipher   cipher   = new TextSecureCipher(new MyAxolotlStore());
    TextSecureMessage message   = cipher.decrypt(envelope);

    System.out.println("Received message: " + message.getBody().get());
  }

} finally {
  if (messagePipe != null)
    messagePipe.close();
}
`````

# Legal things

## Cryptography Notice

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted.
See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.
The form and manner of this distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

## License

Copyright 2013-2015 Open Whisper Systems

Licensed under the AGPLv3: https://www.gnu.org/licenses/agpl-3.0.html

