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

long              recipientId = getRecipientIdFor("+14159998888");
TextSecureAddress destination = new TextSecureAddress(recipientId, "+14159998888", null);
TextSecureMessage message     = new TextSecureMessage(System.currentTimeMillis(), "Hello, world!");

messageSender.sendMessage(destination, message);
`````

## Sending media messages

`````
TextSecureMessageSender messageSender = new TextSecureMessageSender(URL, TRUST_STORE, USERNAME, PASSWORD,
                                                                    localRecipientId, new MyAxolotlStore(),
                                                                    Optional.absent());

long              recipientId = getRecipientIdFor("+14159998888");
TextSecureAddress destination = new TextSecureAddress(recipientId, "+14159998888", null);

File                 myAttachment     = new File("/path/to/my.attachment");
FileInputStream      attachmentStream = new FileInputStream(myAttachment);
TextSecureAttachment attachment       = new TextSecureAttachmentStream(attachmentStream, "image/png", myAttachment.size());
TextSecureMessage    message          = new TextSecureMessage(System.currentTimeMillis(), attachment, "Hello, world!");

messageSender.sendMessage(destination, message);

`````

## Receiving messages

`````
TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, USERNAME, PASSWORD, mySignalingKey);
TextSecureMessagePipe     messagePipe;

try {
  messagePipe = messageReciever.createMessagePipe();

  while (listeningForMessages) {
    TextSecureEnvelope envelope = messagePipe.read(timeout, timeoutTimeUnit);
    TextSecureCipher   cipher   = new TextSecureCipher(new MyAxolotlStore(),
                                                       getRecipientIdFor(envelope.getSource()),
                                                       envelope.getSourceDevice());
    TextSecureMessage message   = cipher.decrypt(envelope);

    System.out.println("Received message: " + message.getBody().get());
  }

} finally {
  if (messagePipe != null)
    messagePipe.close();
}
`````