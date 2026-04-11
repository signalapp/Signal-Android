# Signal Android: Current Account & Identity Model

## Overview

Signal Android uses a **single-account-per-installation** model. Each app installation is bound to exactly one phone number, one ACI (Account Creator Identifier), and one PNI (Phone Number Identifier). There is no built-in concept of switching between accounts or holding multiple identities simultaneously.

The account model spans five layers: identity, authentication, cryptographic keys, local storage, and server communication.

---

## 1. Identity Model

### Three Identifiers Per Account

Every Signal account is identified by three values:

| Identifier | Format | Lifetime | Purpose |
|---|---|---|---|
| **ACI** (Account Creator Identifier) | UUID | Permanent (survives number changes) | Primary account identity for messages, groups, profiles |
| **PNI** (Phone Number Identifier) | UUID prefixed with `PNI:` | Changes when phone number changes | Allows others to message you by phone number before you share your ACI |
| **E164** | Phone number string (e.g. `+12125551234`) | Changes on number change | Human-readable identifier, used for contact discovery |

These are defined in `core/models-jvm/src/main/java/org/signal/core/models/ServiceId.kt`:
- `ServiceId` is a sealed class with two implementations: `ACI` and `PNI`
- Both wrap a UUID but serialize differently (`PNI` gets a `PNI:` prefix)

### ServiceIds Container

`ServiceIds` (in `lib/libsignal-service/.../push/ServiceIds.java`) bundles ACI + PNI together as a pair, used whenever the app needs to refer to "all of my identities."

### SignalServiceAddress

`SignalServiceAddress` (in `lib/libsignal-service/.../push/SignalServiceAddress.java`) combines a `ServiceId` + optional `E164` to represent a message destination or origin. This is the standard way users are identified in the protocol layer.

### Self-Identification

The app determines "who am I" by comparing recipient records against stored account values:

```kotlin
// RecipientTable.kt:3035
private fun isSelf(e164: String?, pni: PNI?, aci: ACI?): Boolean {
  return (e164 != null && e164 == SignalStore.account.e164) ||
         (pni != null && pni == SignalStore.account.pni) ||
         (aci != null && aci == SignalStore.account.aci)
}
```

The self-recipient is resolved at runtime via `LiveRecipientCache.getSelf()`, which looks up the local ACI or E164 in the `RecipientTable` and caches the result.

---

## 2. Local Storage

### Two Encrypted Databases

| Database | File | Encryption | Purpose |
|---|---|---|---|
| **Main database** | `signal.db` | SQLCipher | Messages, recipients, threads, groups, sessions, pre-keys |
| **Key-value store** | `signal-key-value.db` | SQLCipher | Account credentials, identity keys, settings, feature flags |

Both are encrypted with a `DatabaseSecret` derived from device credentials. There is **no per-account isolation** -- one database holds everything for the single account.

### Account Values (Key-Value Store)

`AccountValues.kt` stores all account identity and credential data:

| Key | Value | Purpose |
|---|---|---|
| `account.aci` | UUID string | Local user's ACI |
| `account.pni` | PNI UUID string | Local user's PNI |
| `account.e164` | Phone number | Local user's phone number |
| `account.service_password` | Base64 (18 random bytes) | HTTP Basic Auth password |
| `account.registration_id` | Integer | ACI registration ID (message ordering) |
| `account.pni_registration_id` | Integer | PNI registration ID |
| `account.device_id` | Integer | Device ID (1 = primary) |
| `account.is_registered` | Boolean | Whether registered with server |
| `account.has_linked_devices` | Boolean | Multi-device status |
| `account.fcm_token` | String | Firebase push token |
| `account.account_entropy_pool` | String | Master entropy for key derivation |
| `account.aci_identity_public_key` | Blob | ACI identity public key |
| `account.aci_identity_private_key` | Blob | ACI identity private key |
| `account.pni_identity_public_key` | Blob | PNI identity public key |
| `account.pni_identity_private_key` | Blob | PNI identity private key |

### Recipient Table

The `RecipientTable` in `signal.db` stores all known contacts (including self) with unique constraints on identity columns:

```
_id              INTEGER PRIMARY KEY
aci              TEXT UNIQUE NULLABLE    -- ACI as UUID string
pni              TEXT UNIQUE NULLABLE    -- PNI as "PNI:UUID" string
e164             TEXT UNIQUE NULLABLE    -- Phone number
username         TEXT UNIQUE NULLABLE    -- Signal username
group_id         TEXT UNIQUE NULLABLE    -- For group recipients
type             INTEGER                 -- INDIVIDUAL, GROUP, DISTRIBUTION_LIST, CALL_LINK
registered       INTEGER                 -- RegisteredState enum
profile_key      TEXT                    -- Profile encryption key
storage_service_id TEXT UNIQUE           -- Storage service sync ID
... (50+ additional columns for settings, profile data, etc.)
```

### Recipient Merging

When the app learns that multiple identity fragments (ACI, PNI, E164) belong to the same person, `RecipientTable.getAndPossiblyMerge()` consolidates them into a single row. This is a complex state machine that handles scenarios like:
- Learning an ACI for a previously E164-only contact
- Phone number changes (PNI reassignment)
- Self-identity changes during number change flow

### Thread and Message Tables

- **ThreadTable**: One row per conversation, linked to a `RecipientId` via foreign key. Conversations are recipient-centric, not account-centric.
- **MessageTable**: Each message has `from_recipient_id` (sender) and `to_recipient_id` (recipient), both foreign keys to `RecipientTable`. The self-recipient appears as sender for outgoing messages.

---

## 3. Cryptographic Key Architecture

### Dual Identity Keys

Signal maintains **completely separate** cryptographic identities for ACI and PNI:

```
ACI Identity KeyPair ── used for all message encryption under your account identity
PNI Identity KeyPair ── used when someone messages your phone number (before ACI exchange)
```

Both are `IdentityKeyPair` objects from libsignal, generated during registration. The PNI identity key changes when you change phone numbers; the ACI identity key is permanent.

### Protocol Stores (Dual Architecture)

`SignalServiceDataStoreImpl` wraps two independent `SignalServiceAccountDataStoreImpl` instances -- one for ACI, one for PNI. Each contains:

| Store | Purpose |
|---|---|
| `TextSecurePreKeyStore` | One-time EC pre-keys |
| `SignalKyberPreKeyStore` | Post-quantum Kyber pre-keys |
| `SignalIdentityKeyStore` | Identity key (reports ACI or PNI key via supplier) |
| `TextSecureSessionStore` | Per-device session state |
| `SignalSenderKeyStore` | Group encryption sender keys |

The underlying `SignalBaseIdentityKeyStore` (which caches remote contact identities) is shared, but each wrapper reports its own identity key pair.

### Pre-Key Management

Pre-keys are generated separately for ACI and PNI:

| Key Type | Batch Size | Rotation | Purpose |
|---|---|---|---|
| One-time EC | 100 | On depletion | Forward secrecy for new sessions |
| Signed EC | 1 | Every 30 days | Long-term session establishment |
| Kyber (one-time) | 100 | On depletion | Post-quantum forward secrecy |
| Kyber (last resort) | 1 | Permanent fallback | Post-quantum fallback |

Metadata (next IDs, active keys, registration status) tracked separately per identity in `AccountValues.aciPreKeys` and `AccountValues.pniPreKeys`.

### Account Entropy Pool (AEP)

A 32-byte random value (`LibSignalAccountEntropyPool.generate()`) that serves as the root entropy for deriving:
- **Master Key** -- used for storage service encryption and registration lock
- **Registration Recovery Password** -- for account recovery without phone verification
- **Registration Lock Token** -- derived from Master Key + PIN via SVR

### SVR (Secure Value Recovery)

PIN-based account protection:
1. User's PIN is stored in a secure enclave (SVR2/SVR3)
2. On new registration, PIN unlocks the Master Key from SVR
3. Master Key derives a Registration Lock Token
4. Token proves account ownership to the server

---

## 4. Authentication & Server Communication

### HTTP Authentication

All API requests use **HTTP Basic Auth**:

```
Authorization: Basic base64(identifier:password)

where:
  identifier = ACI (UUID string) -- or E164 during registration
  if linked device: identifier = ACI.deviceId
  password   = stored service password (18 random bytes, base64)
```

Implemented in `PushServiceSocket.java`, which uses `CredentialsProvider` to obtain credentials.

### WebSocket (Persistent Connection)

The app maintains a persistent authenticated WebSocket for real-time message delivery:

- URI: `wss://[service-url]/v1/websocket/`
- Authentication: Same HTTP Basic Auth headers at connection time
- Keep-alive: Every 30 seconds
- Two implementations:
  - `OkHttpWebSocketConnection` -- original OkHttp-based
  - `LibSignalChatConnection` -- newer libsignal-net-based

`IncomingMessageObserver` manages the WebSocket lifecycle, reading message batches and routing them to decryption.

### Message Routing

Incoming messages arrive as protobuf `Envelope` objects with:
- `destinationServiceId` -- the ACI (or PNI) this message is addressed to
- `sourceServiceId` -- the sender's ACI/PNI
- `sourceDeviceId` -- which of the sender's devices sent it

The `destinationServiceId` is the field that routes messages to the correct account.

### Key Server Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/v1/registration` | POST | Register new account |
| `/v1/verification/session` | POST | Create phone verification session |
| `/v1/verification/session/{id}/code` | PUT | Submit verification code |
| `/v2/accounts/number` | PUT | Change phone number |
| `/v1/accounts/attributes` | PUT | Update account attributes |
| `/v1/accounts/whoami` | GET | Get current account info |
| `/v1/accounts/registration_lock` | PUT/DELETE | Enable/disable registration lock |
| `/v1/devices` | GET | List linked devices |
| `/v1/devices/{id}` | DELETE | Remove linked device |
| `/v1/provisioning/{id}` | PUT | Link new device |

---

## 5. Registration & Number Change Flows

### New Registration

1. **Session creation**: `POST /v1/verification/session` with E164 + FCM token
2. **Code request**: SMS or voice call verification code
3. **Code submission**: Proves phone number ownership
4. **Account creation**: `POST /v1/registration` with:
   - Generated ACI + PNI identity key pairs
   - Signed pre-keys + Kyber pre-keys for both identities
   - Registration IDs (random per identity)
   - Service password (random 18 bytes)
   - Account Entropy Pool
   - FCM token, device capabilities
5. **Server returns**: Assigned ACI, PNI, confirmed E164
6. **Local storage**: All values written to `AccountValues`

### Phone Number Change

1. Verify new phone number (same session/code flow)
2. Generate **new PNI identity key pair** + new PNI pre-keys + new PNI registration ID
3. Create `SyncMessage.PniChangeNumber` encrypted for each linked device
4. Submit `PUT /v2/accounts/number` with new E164, new PNI keys, sync messages
5. Server returns updated ACI (same), new PNI, new E164
6. Update local `AccountValues`, rotate certificates, refresh account attributes

**Key point**: ACI stays the same. PNI changes. All PNI-related cryptographic material is regenerated.

### Linked Devices

- Primary device (device ID 1) provisions secondary devices via QR code
- Provisioning sends: ACI/PNI identity key pairs, Account Entropy Pool, E164, ACI, PNI
- Secondary devices share the same ACI and service password
- Each device has its own device ID, pre-keys, and sessions
- Device list managed via `/v1/devices` endpoints

---

## 6. Key Architectural Characteristics

1. **Single account per installation**: No concept of account switching. All state assumes one account.
2. **ACI is the durable identity**: Survives phone number changes. Used for group membership, profile keys, message history.
3. **PNI enables phone-number-based messaging**: Separate cryptographic identity allows messaging by phone number without revealing ACI.
4. **Flat recipient model**: All contacts (including self) are rows in one `RecipientTable`. Self is identified dynamically by matching ACI/E164.
5. **Monolithic database**: Single `signal.db` holds all data. No sharding or partitioning by account.
6. **Credential-based auth**: One set of credentials (ACI + password) authenticates all requests. No token refresh or OAuth -- the password is static until changed.
7. **Dual protocol stores**: Separate session/pre-key state for ACI and PNI, but shared underlying storage.

---

## Key Source Files

| File | Purpose |
|---|---|
| `core/models-jvm/.../ServiceId.kt` | ACI/PNI type definitions |
| `lib/libsignal-service/.../SignalServiceAddress.java` | ServiceId + E164 address |
| `lib/libsignal-service/.../ServiceIds.java` | ACI + PNI container |
| `app/.../keyvalue/AccountValues.kt` | Local account credential storage |
| `app/.../keyvalue/SignalStore.kt` | Singleton access to all key-value stores |
| `app/.../recipients/Recipient.kt` | Immutable recipient snapshot |
| `app/.../recipients/RecipientId.kt` | Database row ID + cache |
| `app/.../recipients/LiveRecipientCache.java` | Observable recipient access + self lookup |
| `app/.../database/RecipientTable.kt` | Recipient storage + merging logic |
| `app/.../database/SignalDatabase.kt` | Main database singleton |
| `app/.../database/KeyValueDatabase.java` | Key-value store database |
| `app/.../crypto/storage/SignalServiceDataStoreImpl.java` | Dual ACI/PNI protocol stores |
| `app/.../crypto/storage/SignalServiceAccountDataStoreImpl.java` | Per-identity protocol store |
| `app/.../crypto/PreKeyUtil.java` | Pre-key generation |
| `lib/libsignal-service/.../PushServiceSocket.java` | HTTP API layer |
| `lib/libsignal-service/.../SignalWebSocket.kt` | WebSocket wrapper |
| `app/.../messages/IncomingMessageObserver.kt` | WebSocket message receiver |
| `feature/registration/.../RegistrationRepository.kt` | Registration flow |
| `app/.../changenumber/ChangeNumberRepository.kt` | Number change flow |
| `app/.../keyvalue/SvrValues.kt` | PIN/SVR storage |
