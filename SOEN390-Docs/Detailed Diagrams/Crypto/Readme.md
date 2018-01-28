# Crypto Component

This diagram explains the structure of Crypto component and the way the subcomponents work together to provide the interface that is used in the main application

### Diagram Summary:

- ''MasterSecret'' is the object that is being exchanged between the presentation components and the business logic components.

- ''MasterCipher'' is the main class that responsible for crypto services in the in the application. types of encryptions are 
1) 16 byte random IV.
2) AES-CBC(plaintext)
3) HMAC-SHA1 of 1 and 2

- ''Key Manager'' is a number of classes such "Public key, IdentityKeyUtil, AsymmetricMasterSecret" that work togther to manage application storage and communication security.
When a user first initializes TextSecure, a few secrets
are generated.  These are:
1) A 128bit symmetric encryption key.
2) A 160bit symmetric MAC key.
3) An ECC keypair. 
The first two, along with the ECC keypair's private key, are
then encrypted on disk using PBE.

### Sample Code Use:

- [Creating the MasterCipher obj using masterSecret](https://github.com/signalapp/Signal-Android/blob/0a569676f7a57144374a24faef566b2ca3233290/src/org/thoughtcrime/securesms/crypto/IdentityKeyUtil.java#L120)
 ````java
MasterCipher masterCipher   = new MasterCipher(masterSecret);
````
- [Using encryption methods](https://github.com/signalapp/Signal-Android/blob/2add02c62f39db39484c13158d3d45b4ef1d7491/src/org/thoughtcrime/securesms/database/SmsMigrator.java#L165)
 ````java
String body = "hello world";
masterCipher.encryptBody(body);
````


