
# Overview

The axolotl protocol is a "ratcheting" forward secrecy protocol that works in synchronous and
asynchronous messaging environments.  The protocol overview is available [here](https://github.com/trevp/axolotl/wiki),
and the details of the wire format are available [here](https://github.com/WhisperSystems/TextSecure/wiki/ProtocolV2).

## PreKeys

This protocol implementation heavily leverages the concept of PreKeys.  A PreKey is simply an
ECPublicKey and unique PreKey ID.  At install time, clients generate a large list of PreKeys
and transmit them to the server.  The server will then remove PreKeys from that list and hand
them to other clients when requested.  A single PreKey can never be used twice.

## Sessions

The axolotl protocol is session-oriented.  Clients establish a "session," which is then used for
all subsequent encrypt/decrypt operations.  There is no need to ever tear down a session once one
has been established.

Sessions are established in one of three ways:

1. PreKeys. A client that wishes to send a message to a recipient can establish a session by
   retrieving a PreKey for that recipient from the server.
1. PreKeyWhisperMessages.  A client can receive a PreKeyWhisperMessage from a recipient and use it
   to establish a session.
1. KeyExchangeMessages.  Two clients can exchange KeyExchange messages to establish a session.

## State

An established session encapsulates a lot of state between two clients.  That state is maintained
in durable records which need to be kept for the life of the session.

State is kept in the following places:

1. Identity State.  Clients will need to maintain the state of their own identity key pair, as well
   as identity keys received from other clients.
1. PreKey State. Clients will need to maintain the state of their generated PreKeys.
1. Session State.  Clients will need to maintain the state of the sessions they have established.

# Using libaxolotl

## Install time

At install time, a libaxolotl client needs to generate its identity keys, registration id, and
prekeys.

```
IdentityKeyPair    identityKeyPair = KeyHelper.generateIdentityKeyPair();
int                registrationId  = KeyHelper.generateRegistrationId();
List<PreKeyRecord> preKeys         = KeyHelper.generatePreKeys(startId, 100);

// Store identityKeyPair somewhere durable and safe.
// Store registrationId somewhere durable and safe.
// Store preKeys somewhere durable and safe.

```

## Building a session

A libaxolotl client needs to implement three interfaces: IdentityKeyStore, PreKeyStore, and
SessionStore.  These will manage loading and storing of identity, prekeys, and session state.

Once those are implemented, building a session is fairly straightforward:

```
SessionStore     sessionStore  = new MySessionStore();
PreKeyStore      preKeyStore   = new MyPreKeyStore();
IdentityKeyStore identityStore = new MyIdentityKeyStore();

// Instantiate a SessionBuilder for a remote recipientId + deviceId tuple.
SessionBuilder sessionBuilder = new SessionBuilder(sessionStore, preKeyStore, identityStore,
                                                   recipientId, deviceId);

// Build a session with a PreKey retrieved from the server.
sessionBuilder.process(retrievedPreKey);

SessionCipher sessionCipher = new SessionCipher(sessionStore, recipientId, deviceId);
CiphertextMessage message = sessionCipher.encrypt("Hello world!".getBytes("UTF-8"));

deliver(message.serialize());
```