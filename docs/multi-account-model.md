# Multi-Account Support: Design Approaches

## Constraint

**The Signal server cannot change.** Each phone number is a fully independent Signal account with its own ACI, PNI, credentials, and server-side state. "Multi-number" therefore means "multiple independent Signal accounts managed within a single app installation."

## Terminology

| Term | Meaning |
|---|---|
| **Account** | A complete Signal identity: its own ACI, PNI, E164, credentials, identity keys, pre-keys, sessions |
| **Active account** | The account currently in the foreground for user interaction |
| **Background account** | An account not currently foregrounded but potentially still receiving messages |
| **Account container** | The set of singletons, databases, and network connections serving one account |

---

## Architectural Reality

Signal Android was built as a single-account app. Understanding the depth of this assumption is critical before evaluating approaches.

### Hard Singletons (One Instance Per Process)

| Singleton | How Accessed | What It Holds |
|---|---|---|
| `SignalDatabase` | `SignalDatabase.messages`, `.recipients()`, etc. (static) | Single `signal.db` -- all messages, contacts, threads, sessions, pre-keys |
| `SignalStore` | `SignalStore.account()`, `.settings()`, etc. (static) | Single `signal-key-value.db` -- ACI, PNI, E164, service password, identity keys |
| `AppDependencies` | `AppDependencies.jobManager`, `.recipientCache`, etc. (Kotlin object) | All app-wide services, initialized once in `ApplicationContext.onCreate()` |
| `JobManager` | `AppDependencies.jobManager` | Single job queue backed by `JobDatabase` |
| `IncomingMessageObserver` | Via `NetworkDependenciesModule` | Single WebSocket pair (auth + unauth) |
| `LiveRecipientCache` | `AppDependencies.recipientCache` | Single LRU cache reading from single `RecipientTable` |
| `DatabaseObserver` | `AppDependencies.databaseObserver` | Single registry of UI/background observers |

### Resettable Components

The `NetworkDependenciesModule` is wrapped in a `resettableLazy` and can be torn down and rebuilt:
- WebSocket connections
- `SignalServiceAccountManager`
- `SignalServiceMessageSender`
- Protocol stores
- All API clients

This is currently used for re-registration flows and is the most promising seam for multi-account.

### FCM Constraint

- One FCM token per app installation (not per account)
- FCM pushes carry no message payload -- they just wake the app to drain the WebSocket
- Token registered with server via `PUT /v1/accounts/gcm`
- For multi-account: the same FCM token must be registered with every account's server-side record, and on wake, the app must drain all accounts' WebSockets

---

## What Must Change Regardless of Approach

1. **Account registry**: A new lightweight store (outside any account's database) that maps account index to ACI/E164/display-name, used for the account switcher UI and boot-time enumeration.

2. **FCM multiplexing**: On any FCM wake, all accounts must be drained, not just the active one. The same FCM token must be registered with each account on the server.

3. **Notification routing**: Notifications must indicate which account received the message. Separate notification groups or channels per account.

4. **Self-identification**: If accounts share a database, `isSelf()` must match any of the user's ACIs/PNIs/E164s. If databases are isolated, this is unchanged per-account.

5. **UI chrome**: Account switcher (e.g., avatar in toolbar), per-account unread badges, account indicator on conversations and messages.

---

## Approach 1: Account Switching (Hot Swap)

### Concept

Only one account is active at a time. Switching accounts tears down the current singleton stack and rebuilds it for the new account. Background accounts are dormant -- they don't receive messages in real time. This is similar to how many email clients handle multiple accounts on mobile.

### How It Works

Each account gets its own directory:
```
/data/data/org.thoughtcrime.securesms/
  account-registry.db          ← new: lightweight, lists all accounts
  accounts/
    account-0/
      signal.db
      signal-key-value.db
      attachments/
    account-1/
      signal.db
      signal-key-value.db
      attachments/
```

On switch:
1. Disconnect WebSocket, flush job queue, close database
2. Update `activeAccountId` in account registry
3. Re-initialize `SignalDatabase`, `SignalStore`, `NetworkDependenciesModule` pointing to new account's files
4. Reconnect WebSocket with new credentials
5. Rebuild UI state (conversation list, etc.)

### Singleton Changes Required

| Component | Change |
|---|---|
| `SignalDatabase` | Add `reinit()` or `switchTo(path)` method. Close old DB, open new. |
| `SignalStore` | Add `reinit()`. Close old KV store, open new. |
| `AppDependencies` | Add `resetAll()` that cascades through all lazy properties. Network module already supports `reset()`. |
| `JobManager` | Flush, then reinit with new `JobDatabase` path. |
| `LiveRecipientCache` | Clear on switch (it reads from the now-different database). |
| `DatabaseObserver` | Clear all observers on switch (UI will re-register). |

### FCM Handling

- Register same FCM token with all accounts' servers
- On FCM wake: quickly cycle through each account, connect WebSocket, drain messages, disconnect
- Only the active account gets a persistent WebSocket; others get periodic drain passes
- Risk: messages to background accounts are delayed until the next FCM wake + drain cycle

### Adding a New Account

Standard Signal registration flow, but:
1. Create new account directory
2. Switch to it (re-initialize singletons)
3. Run normal registration (verify phone, generate keys, register with server)
4. Switch back to original account (or stay on new one)

### Pros
- **Minimizes concurrent resource usage** -- only one database open, one WebSocket, one job queue
- **Each account is a standard Signal account** -- no changes to how messages, contacts, or crypto work within an account
- **Existing per-account code is untouched** -- the singleton stack works exactly as today, just re-pointed
- **Simplest mental model** -- like switching between browser profiles
- **NetworkDependenciesModule already supports reset** -- the primary seam already exists

### Cons
- **Background accounts miss real-time messages** -- delayed until next drain cycle
- **Switching is slow** -- database close/open, WebSocket reconnect, UI rebuild (likely 1-3 seconds)
- **Singleton re-initialization is fragile** -- any component that caches a reference to the old database/store will break
- **FCM drain cycling burns battery** -- waking N WebSockets on every push
- **No unified conversation list** -- user must switch to see each account's messages

### Complexity: Medium

### Key Files to Modify
- `SignalDatabase.kt` -- add reinit/switchTo capability
- `KeyValueDatabase.java` -- add reinit capability
- `SignalStore.kt` -- add reinit capability
- `AppDependencies.kt` -- add full reset cascade
- `ApplicationContext.java` -- account-aware initialization
- `FcmReceiveService.java` -- drain all accounts on wake
- `FcmFetchManager.kt` -- iterate accounts during drain
- `NotificationChannels.java` -- per-account channel groups
- New: `AccountRegistry.kt`, `AccountSwitcher.kt`, account switcher UI

---

## Approach 2: Parallel Account Containers

### Concept

All accounts run simultaneously. Each account gets its own complete "container" of dependencies -- its own database, key-value store, WebSocket, protocol store, and job queue. A router layer multiplexes between containers. All accounts receive messages in real time.

### Architecture

```kotlin
class AccountContainer(val accountId: String) {
    val database: SignalDatabase        // account's signal.db
    val keyValueStore: SignalStore       // account's signal-key-value.db
    val networkModule: NetworkDependenciesModule  // account's WebSocket + APIs
    val jobManager: JobManager          // account's job queue
    val protocolStore: SignalServiceDataStoreImpl
    val recipientCache: LiveRecipientCache
    val messageObserver: IncomingMessageObserver
}

object AccountContainerRegistry {
    private val containers = mutableMapOf<String, AccountContainer>()
    var activeAccountId: String  // which account is in the foreground

    fun getActive(): AccountContainer
    fun getAll(): List<AccountContainer>
    fun get(accountId: String): AccountContainer
    fun add(accountId: String): AccountContainer
    fun remove(accountId: String)
}
```

### Singleton Elimination

The core challenge: all of Signal's singletons must become container-scoped. Two sub-approaches:

**Option A: Instance-based refactor**

Replace all static access with instance access through the container:
```kotlin
// Before (pervasive throughout codebase)
SignalDatabase.messages.getMessages(threadId)
SignalStore.account().requireAci()

// After
container.database.messages.getMessages(threadId)
container.keyValueStore.account().requireAci()
```

This touches essentially every file in the app. Thousands of call sites.

**Option B: Thread-local / coroutine-context routing**

Keep static access patterns but route them to the correct container based on calling context:
```kotlin
object SignalDatabase {
    // Instead of a single instance, route to the correct container's DB
    val instance: SignalDatabaseInstance
        get() = AccountContainerRegistry.currentContainer().database
}
```

Where `currentContainer()` is determined by:
- A thread-local variable set at the entry point of each operation
- A coroutine context element for suspend functions
- The active account for UI-initiated operations

This is less invasive but harder to reason about and debug.

### Resource Implications

For N accounts simultaneously:
- N open SQLCipher databases (each consumes ~2-4 MB of memory for page cache)
- N WebSocket connections (persistent TCP + TLS state)
- N job queues running concurrently
- N sets of pre-key maintenance jobs
- N recipient caches

### FCM Handling

- Same FCM token registered with all accounts
- On FCM wake: all WebSockets are already connected (or reconnect)
- Each account's `IncomingMessageObserver` independently drains its own messages
- No cycling needed -- truly concurrent

### Pros
- **Real-time message delivery for all accounts** -- no delays, no drain cycling
- **Cleanest conceptual model** -- each account is fully independent, just like running N copies of the app
- **No switch latency** -- changing the active account is just a UI state change
- **Could support unified inbox** -- a view that merges conversation lists across containers

### Cons
- **Enormous refactoring effort** -- either thousands of call sites change (Option A) or a complex routing layer is introduced (Option B)
- **High resource usage** -- N x databases, WebSockets, job queues, caches
- **Memory pressure on low-end devices** -- each additional account adds significant baseline memory
- **Testing surface explodes** -- every feature must be validated in multi-container context
- **Subtle bugs from container leaks** -- any code that captures a reference across container boundaries creates hard-to-debug issues

### Complexity: Very High

### Key Files to Modify
- Effectively **the entire codebase** touches `SignalDatabase` or `SignalStore` statically
- `SignalDatabase.kt` -- make instantiable (not singleton)
- `SignalStore.kt` -- make instantiable
- `AppDependencies.kt` -- replace with `AccountContainerRegistry`
- `ApplicationDependencyProvider.kt` -- becomes per-container factory
- `JobManager.java` -- per-container instances
- `IncomingMessageObserver.kt` -- per-container instances
- All jobs, repositories, view models -- must get their dependencies from a container
- New: `AccountContainer.kt`, `AccountContainerRegistry.kt`

---

## Approach 3: Hybrid -- Active Foreground + Lightweight Background Receivers (Recommended)

### Concept

The active account runs with the full singleton stack (exactly as today). Background accounts run with a **minimal receiver** -- just a WebSocket connection, message decryption, and database insertion, using their own isolated mini-database. The full stack is only loaded for the account the user is currently interacting with.

This is a pragmatic middle ground: one full-fat account container + N lightweight background listeners.

### Architecture

```kotlin
// Full stack for active account (existing singletons, unchanged)
// Active account uses SignalDatabase, SignalStore, etc. as-is

// Lightweight background receiver per inactive account
class BackgroundAccountReceiver(val accountId: String) {
    val database: SQLiteDatabase        // account's signal.db (opened read/write but minimal usage)
    val credentials: CredentialsProvider // account's ACI + password
    val webSocket: SignalWebSocket.AuthenticatedWebSocket
    val protocolStore: SignalServiceDataStoreImpl
    
    // Minimal operations:
    fun connect()           // open WebSocket
    fun disconnect()        // close WebSocket
    fun drainMessages()     // receive, decrypt, insert into DB
    fun postNotification()  // show notification for new messages
}

class AccountManager {
    val activeAccountId: String
    val backgroundReceivers: Map<String, BackgroundAccountReceiver>
    
    fun switchTo(accountId: String) {
        // 1. Tear down active account's singletons
        // 2. Stop the background receiver for the target account
        // 3. Re-init singletons with target account's data
        // 4. Start a background receiver for the previously-active account
    }
}
```

### What the Background Receiver Does

The background receiver is deliberately limited:
1. Maintain a WebSocket connection using the account's credentials
2. Receive encrypted envelopes
3. Decrypt them using the account's protocol store (opened directly, not via singletons)
4. Insert decrypted messages into the account's `signal.db` (direct SQL, not via `SignalDatabase` singleton)
5. Post a notification with the sender and message preview
6. That's it. No jobs, no sync, no storage service, no group updates, no profile fetches.

When the user switches to that account, the full singleton stack loads and catches up on anything the background receiver couldn't handle (group state, profile updates, read receipts, etc.).

### Directory Layout

Same as Approach 1:
```
accounts/
  account-0/
    signal.db
    signal-key-value.db
  account-1/
    signal.db
    signal-key-value.db
account-registry.db
```

### Singleton Changes

The active account's singletons are **unchanged from today's code**. The only new code is:
- The `BackgroundAccountReceiver` (new class, doesn't use singletons)
- The `AccountManager` that orchestrates switching
- Re-init methods on `SignalDatabase`, `SignalStore`, `AppDependencies` (same as Approach 1)

### FCM Handling

- Same token registered with all accounts
- On FCM wake:
  - Active account's `IncomingMessageObserver` drains normally (already connected)
  - Each `BackgroundAccountReceiver` drains its WebSocket
- Background receivers can also maintain persistent connections if the device is on WiFi / charging

### Message Handling in Background Receivers

```kotlin
class BackgroundAccountReceiver(val accountId: String) {
    
    fun onEnvelopeReceived(envelope: Envelope) {
        // 1. Decrypt using account's protocol store
        val plaintext = decrypt(envelope)
        
        // 2. If it's a simple message, insert directly
        if (plaintext.isDataMessage) {
            insertMessageDirectly(plaintext)
            showNotification(plaintext)
        }
        
        // 3. If it's something complex (group update, sync message, etc.),
        //    just store the raw envelope for processing when account becomes active
        else {
            storeRawEnvelope(envelope)
        }
    }
}
```

This avoids the need for background receivers to run jobs, update group state, or handle complex protocol operations. Those are deferred to when the user actually switches to the account.

### Pros
- **Real-time notifications for all accounts** -- background receivers catch messages immediately
- **Active account runs unmodified** -- the entire existing singleton stack works as-is
- **Modest resource usage** -- background receivers are lightweight (WebSocket + protocol store, no full DB stack)
- **Clean separation of concerns** -- background receiver is a small, testable, isolated component
- **Graceful degradation** -- if background receiver can't handle something, it defers to the full stack
- **Incremental implementation** -- start with Approach 1 (hot swap), then add background receivers

### Cons
- **Background accounts have degraded functionality** -- no group updates, no profile fetches, no read receipt sync until switched to
- **Account switch still requires singleton re-init** (same latency as Approach 1)
- **Two code paths for message receipt** -- full `IncomingMessageObserver` for active account, simplified `BackgroundAccountReceiver` for others
- **Direct SQL insertion in background receiver is fragile** -- must be kept in sync with `MessageTable` schema
- **N WebSocket connections still consume resources** -- though less than full containers

### Complexity: Medium-High

### Key Files to Modify
- Same as Approach 1 (singleton re-init), plus:
- New: `BackgroundAccountReceiver.kt` -- lightweight message receiver
- New: `AccountManager.kt` -- orchestrates active/background state
- `FcmReceiveService.java` / `FcmFetchManager.kt` -- wake all receivers
- `NotificationChannels.java` -- per-account notification channels
- `DefaultMessageNotifier.kt` -- account-aware notifications

---

## Approach 4: Multi-Tenant Database

### Concept

Instead of separate databases per account, use a **single database** with an `account_id` column on every table. All accounts share one `signal.db`. The singletons remain singletons but become account-aware.

### Schema Changes

Every table gets an `account_id` column:

```sql
-- Example for message table
ALTER TABLE message ADD COLUMN account_id TEXT NOT NULL DEFAULT 'legacy';

-- Every query gets a WHERE clause
SELECT * FROM message WHERE account_id = ? AND thread_id = ? ORDER BY date_received DESC;

-- Indexes must include account_id
CREATE INDEX message_account_thread ON message(account_id, thread_id, date_received);
```

Similarly for: `recipient`, `thread`, `sessions`, `one_time_prekeys`, `signed_prekeys`, `kyber_prekey`, `sender_keys`, `identities`, `groups`, `group_membership`, `drafts`, `attachment`, `reaction`, `call`, etc.

### AccountValues Changes

`SignalStore` becomes account-scoped:
```kotlin
object SignalStore {
    var activeAccountId: String
    
    fun account(): AccountValues = accountValuesFor(activeAccountId)
    fun account(accountId: String): AccountValues = accountValuesFor(accountId)
}
```

Key-value store entries get account prefixes: `account.0.aci`, `account.0.e164`, `account.1.aci`, etc.

### Query Layer Changes

Every `SignalDatabase` table accessor must inject `account_id`:

```kotlin
// Before
fun getMessages(threadId: Long): List<MessageRecord> {
    return readableDatabase.query("SELECT * FROM message WHERE thread_id = ?", threadId)
}

// After
fun getMessages(threadId: Long, accountId: String = SignalStore.activeAccountId): List<MessageRecord> {
    return readableDatabase.query("SELECT * FROM message WHERE account_id = ? AND thread_id = ?", accountId, threadId)
}
```

### Pros
- **Single database file** -- simpler backup, no directory management
- **Could support cross-account views** -- unified inbox query across all `account_id` values
- **No singleton re-initialization** -- database and store stay open, just change the active account filter
- **Instant account switching** -- just change `activeAccountId`, UI re-queries

### Cons
- **Massive migration** -- every table, every query, every index must be updated
- **Performance risk** -- adding a column + condition to every query, larger indexes
- **RecipientId conflicts** -- two accounts may assign different `RecipientId` values to the same contact. The same person (same ACI) could be `RecipientId(42)` in account 0 and `RecipientId(87)` in account 1 within the same table.
- **Recipient merging complexity** -- merge logic must be scoped per-account
- **Risk of data leaks between accounts** -- a missing `WHERE account_id = ?` in any query exposes cross-account data
- **Pre-key table `account_id` column overloaded** -- currently means ACI-vs-PNI, would need to also encode which account
- **Schema version 314+ with dozens of ALTER TABLE statements**
- **Every test must be updated**

### Complexity: Very High

### Key Files to Modify
- Every table class in `database/` -- add account_id column and filter
- `SignalDatabase.kt` -- migration to add account_id everywhere
- `SignalStore.kt` -- per-account key-value scoping
- `RecipientTable.kt` -- per-account recipient namespace
- `ThreadTable.kt` -- per-account threads
- `MessageTable.kt` -- per-account messages
- All query builders and raw SQL
- All database tests

---

## Approach 5: Android User Profiles (OS-Level Isolation)

### Concept

Leverage Android's built-in Work Profile or Managed Profile APIs to run a second instance of Signal in an isolated OS profile. Each profile gets its own app data directory, its own process space, and its own FCM token. No code changes to Signal itself.

### How It Works

- Android Work Profiles (since Android 5.0) create an isolated container
- The same APK is installed in both the personal profile and work profile
- Each instance has its own `/data/data/`, its own database, its own registration
- The OS handles process isolation, notification routing, and app switching
- Third-party apps like "Shelter" or "Island" can create work profiles without an MDM

### Pros
- **Zero code changes to Signal** -- each instance is a standard single-account app
- **Complete OS-level isolation** -- separate file systems, processes, permissions
- **Already works today** -- users already do this with work profiles
- **Separate FCM tokens per profile** -- no multiplexing needed
- **Separate notifications** -- OS handles per-profile notification routing

### Cons
- **Limited to 2 accounts** (personal + one work profile on most devices)
- **No unified UX** -- the two instances are completely separate apps with no awareness of each other
- **Not a real in-app feature** -- requires the user to understand Android work profiles
- **Work profile has restrictions** -- some device policies may interfere
- **No cross-account features possible** -- can't build a unified inbox, shared contacts, etc.
- **User must manage two separate registrations, two separate PINs, two separate backups**

### Complexity: None (no code changes)

This is worth mentioning as a "zero-effort baseline" -- it's what power users already do today. Any in-app multi-account feature should provide a meaningfully better experience than this.

---

## Comparison Matrix

| Factor | 1: Hot Swap | 2: Parallel Containers | 3: Hybrid (Recommended) | 4: Multi-Tenant DB | 5: OS Profiles |
|---|---|---|---|---|---|
| Server changes | None | None | None | None | None |
| Real-time msgs (all accounts) | No (delayed) | Yes | Yes (notifications) | Yes (if parallel WS) | Yes (separate apps) |
| Switch latency | 1-3 seconds | Instant | 1-3 seconds | Instant | OS app switch |
| Resource usage | Low (1 active) | High (N x all) | Medium (1 full + N lite) | Medium (1 DB, N WS) | High (N x all, OS-level) |
| Codebase impact | Medium | Extreme | Medium-High | Very High | None |
| Unified inbox possible | No | Yes | No (could add later) | Yes | No |
| Account limit | Unlimited | ~3-4 (resource bound) | ~3-4 (resource bound) | Unlimited | 2 |
| Data isolation | Full (separate DBs) | Full | Full | Weak (same DB) | Full (OS-level) |
| Existing code changes | Singleton re-init | Singleton elimination | Singleton re-init + new receiver | Every table + query | None |
| Testing complexity | Medium | Very High | Medium-High | Very High | None |
| Incremental build path | Yes (standalone first) | No (all or nothing) | Yes (builds on #1) | No (all or nothing) | N/A |

---

## Recommended Path

**Start with Approach 1 (Hot Swap), then evolve to Approach 3 (Hybrid).**

### Phase 1: Hot Swap Foundation

This establishes the multi-account infrastructure with the least risk:

1. **Account registry** -- create `account-registry.db` with account index, ACI, E164, display name
2. **Per-account directories** -- `accounts/{id}/signal.db`, `accounts/{id}/signal-key-value.db`
3. **Singleton re-init** -- add `reinit()` to `SignalDatabase`, `SignalStore`; leverage existing `NetworkDependenciesModule.reset()`
4. **Account switch flow** -- disconnect, close, re-init, reconnect
5. **Add-account flow** -- create directory, switch to it, run standard registration
6. **FCM drain cycling** -- on wake, briefly connect each account's WebSocket to drain messages
7. **UI** -- account switcher in conversation list toolbar, per-account notification channels

At this point, users can manage multiple accounts. Background accounts get messages with some delay (on next FCM wake).

### Phase 2: Background Receivers

Add real-time delivery for background accounts:

8. **BackgroundAccountReceiver** -- lightweight WebSocket + decryptor per inactive account
9. **Direct message insertion** -- background receiver inserts messages directly into the account's DB
10. **Background notifications** -- show notifications for background account messages
11. **Deferred processing queue** -- complex messages (group updates, sync) stored as raw envelopes for when account becomes active

### Phase 3 (Optional): Enhanced UX

12. **Unified notification view** -- aggregate unread counts across accounts in a single summary notification
13. **Quick-reply from notification** -- temporarily activate account, send reply, re-activate original
14. **Account badge in conversation list** -- show which account each conversation belongs to when viewing "all accounts" mode

### Why Not Start with Approach 2 or 4?

- **Approach 2 (Parallel Containers)** requires converting the entire codebase away from static singleton access. This is a multi-month refactoring effort that touches every file, and the app must keep working throughout. The risk/reward ratio is poor unless multi-account is the single highest priority for the project.

- **Approach 4 (Multi-Tenant DB)** requires adding an `account_id` column to every table and a filter to every query. A single missed `WHERE` clause leaks data between accounts. The migration is massive and error-prone, and the resulting schema is harder to reason about than separate files.

- **Approach 3 (Hybrid)** builds naturally on top of Approach 1, so starting with Approach 1 doesn't waste work. The background receivers are an additive feature, not a rewrite.

### Estimated Scope

| Phase | New/Modified Files | Effort |
|---|---|---|
| Phase 1 (Hot Swap) | ~15-20 files | Medium (mostly plumbing around existing seams) |
| Phase 2 (Background Receivers) | ~5-8 new files | Medium (new code, isolated from existing) |
| Phase 3 (Enhanced UX) | ~5-10 files | Low-Medium (UI work) |

### Key Risk: Singleton Reference Leaks

The biggest risk in Phase 1 is code that captures a reference to a singleton's internals and survives an account switch. For example:

```kotlin
// Dangerous: captured reference to old database
val messages = SignalDatabase.messages  // captures instance
// ... account switch happens ...
messages.getMessages(threadId)  // now reading from WRONG database
```

Mitigation: `SignalDatabase`'s static accessors should delegate through a mutable reference that switches on re-init, rather than caching table instances. Any code that stores a `MessageTable` reference beyond a single method call must be audited.
