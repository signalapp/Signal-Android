# Razorpay Database Integration Guide

This guide explains how to integrate Razorpay payment data into Signal's database schema while maintaining INR-only currency handling.

## Database Schema Updates

### 1. InAppPaymentTable Changes

The `InAppPaymentTable` needs to be extended with Razorpay-specific columns.

**Location:** `app/src/main/java/org/thoughtcrime/securesms/database/InAppPaymentTable.kt`

Add these columns to track Razorpay payments:

```sql
-- Add to existing table
ALTER TABLE inapp_payment ADD COLUMN razorpay_order_id TEXT UNIQUE;
ALTER TABLE inapp_payment ADD COLUMN razorpay_payment_id TEXT UNIQUE;
ALTER TABLE inapp_payment ADD COLUMN razorpay_signature TEXT;
ALTER TABLE inapp_payment ADD COLUMN amount_in_paisa INTEGER DEFAULT 0;
ALTER TABLE inapp_payment ADD COLUMN currency_code TEXT DEFAULT 'INR';
ALTER TABLE inapp_payment ADD COLUMN payment_method TEXT DEFAULT 'razorpay';
ALTER TABLE inapp_payment ADD COLUMN verification_status TEXT DEFAULT 'pending';
```

**Sample Schema:**

```sql
CREATE TABLE inapp_payment (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  plan_id TEXT NOT NULL,
  amount_in_paisa INTEGER NOT NULL,  -- Store in paisa (e.g., 9900 for ₹99)
  currency_code TEXT DEFAULT 'INR',
  payment_method TEXT DEFAULT 'razorpay',
  razorpay_order_id TEXT UNIQUE,
  razorpay_payment_id TEXT UNIQUE,
  razorpay_signature TEXT,
  verification_status TEXT DEFAULT 'pending',  -- pending, verified, failed
  transaction_status TEXT DEFAULT 'pending',   -- pending, completed, failed
  created_timestamp LONG NOT NULL,
  updated_timestamp LONG NOT NULL,
  FOREIGN KEY(user_id) REFERENCES recipient(uuid)
);
```

### 2. Database Migration File

**Location:** `app/src/main/java/org/thoughtcrime/securesms/database/migration/`

Create migration file `V001_AddRazorpayColumns.kt`:

```kotlin
package org.thoughtcrime.securesms.database.migration

import android.database.sqlite.SQLiteDatabase

object V001_AddRazorpayColumns : Migration {
  override val version = 1
  
  override fun migrate(db: SQLiteDatabase) {
    // Add Razorpay columns to inapp_payment table
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN razorpay_order_id TEXT UNIQUE
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN razorpay_payment_id TEXT UNIQUE
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN razorpay_signature TEXT
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN amount_in_paisa INTEGER DEFAULT 0
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN currency_code TEXT DEFAULT 'INR'
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN payment_method TEXT DEFAULT 'razorpay'
    """)
    
    db.execSQL("""
      ALTER TABLE inapp_payment 
      ADD COLUMN verification_status TEXT DEFAULT 'pending'
    """)
  }
}
```

### 3. Update InAppPaymentData Model

Update the data model to include Razorpay fields:

```kotlin
data class InAppPaymentRecord(
  val id: Long,
  val userId: String,
  val planId: String,
  val amountInPaisa: Int,       // ₹99 = 9900 paisa
  val currencyCode: String = "INR",
  val paymentMethod: String = "razorpay",
  val razorpayOrderId: String?,
  val razorpayPaymentId: String?,
  val razorpaySignature: String?,
  val verificationStatus: VerificationStatus,
  val transactionStatus: TransactionStatus,
  val createdAt: Long,
  val updatedAt: Long
)

enum class VerificationStatus {
  PENDING, VERIFIED, FAILED
}

enum class TransactionStatus {
  PENDING, COMPLETED, FAILED
}
```

## Storing Payment Data

### Insert Payment Record After Successful Payment

```kotlin
class InAppPaymentRepository(private val database: SignalDatabase) {
  
  fun storeRazorpayPayment(
    userId: String,
    planId: String,
    amountInRupees: Int,
    razorpayOrderId: String,
    razorpayPaymentId: String,
    razorpaySignature: String
  ): Long {
    val contentValues = ContentValues().apply {
      put("user_id", userId)
      put("plan_id", planId)
      put("amount_in_paisa", amountInRupees * 100)  // Convert to paisa
      put("currency_code", "INR")
      put("payment_method", "razorpay")
      put("razorpay_order_id", razorpayOrderId)
      put("razorpay_payment_id", razorpayPaymentId)
      put("razorpay_signature", razorpaySignature)
      put("verification_status", "pending")
      put("transaction_status", "completed")
      put("created_timestamp", System.currentTimeMillis())
      put("updated_timestamp", System.currentTimeMillis())
    }
    
    return database.inAppPaymentTable.insert(contentValues)
  }
  
  fun updateVerificationStatus(
    razorpayOrderId: String,
    status: VerificationStatus,
    signatureValid: Boolean
  ) {
    val contentValues = ContentValues().apply {
      put("verification_status", status.name)
      put("updated_timestamp", System.currentTimeMillis())
    }
    
    database.inAppPaymentTable.update(
      contentValues,
      "razorpay_order_id = ?",
      arrayOf(razorpayOrderId)
    )
  }
}
```

## Querying Payment Data

### Get Payment by Order ID

```kotlin
fun getPaymentByOrderId(orderId: String): InAppPaymentRecord? {
  val cursor = database.query(
    "inapp_payment",
    null,
    "razorpay_order_id = ?",
    arrayOf(orderId),
    null,
    null,
    null
  )
  
  return cursor.use { c ->
    if (c.moveToFirst()) {
      InAppPaymentRecord(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        userId = c.getString(c.getColumnIndexOrThrow("user_id")),
        planId = c.getString(c.getColumnIndexOrThrow("plan_id")),
        amountInPaisa = c.getInt(c.getColumnIndexOrThrow("amount_in_paisa")),
        currencyCode = c.getString(c.getColumnIndexOrThrow("currency_code")),
        razorpayOrderId = c.getString(c.getColumnIndexOrThrow("razorpay_order_id")),
        razorpayPaymentId = c.getString(c.getColumnIndexOrThrow("razorpay_payment_id")),
        razorpaySignature = c.getString(c.getColumnIndexOrThrow("razorpay_signature")),
        verificationStatus = VerificationStatus.valueOf(
          c.getString(c.getColumnIndexOrThrow("verification_status"))
        ),
        transactionStatus = TransactionStatus.valueOf(
          c.getString(c.getColumnIndexOrThrow("transaction_status"))
        ),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_timestamp")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_timestamp"))
      )
    } else {
      null
    }
  }
}
```

### Get User's Subscription Plan

```kotlin
fun getUserSubscriptionPlan(userId: String): RazorpayConfig.PaymentPlan? {
  val cursor = database.query(
    "inapp_payment",
    arrayOf("plan_id"),
    "user_id = ? AND transaction_status = ? AND verification_status = ?",
    arrayOf(userId, "COMPLETED", "VERIFIED"),
    null,
    null,
    "created_timestamp DESC",
    "1"
  )
  
  return cursor.use { c ->
    if (c.moveToFirst()) {
      val planId = c.getString(c.getColumnIndexOrThrow("plan_id"))
      RazorpayConfig.getPlanById(planId)
    } else {
      null
    }
  }
}
```

### Get Payment History

```kotlin
fun getPaymentHistory(userId: String, limit: Int = 10): List<InAppPaymentRecord> {
  val payments = mutableListOf<InAppPaymentRecord>()
  
  val cursor = database.query(
    "inapp_payment",
    null,
    "user_id = ?",
    arrayOf(userId),
    null,
    null,
    "created_timestamp DESC",
    limit.toString()
  )
  
  cursor.use { c ->
    while (c.moveToNext()) {
      payments.add(
        InAppPaymentRecord(
          id = c.getLong(c.getColumnIndexOrThrow("id")),
          userId = c.getString(c.getColumnIndexOrThrow("user_id")),
          planId = c.getString(c.getColumnIndexOrThrow("plan_id")),
          amountInPaisa = c.getInt(c.getColumnIndexOrThrow("amount_in_paisa")),
          currencyCode = c.getString(c.getColumnIndexOrThrow("currency_code")),
          razorpayOrderId = c.getString(c.getColumnIndexOrThrow("razorpay_order_id")),
          razorpayPaymentId = c.getString(c.getColumnIndexOrThrow("razorpay_payment_id")),
          razorpaySignature = c.getString(c.getColumnIndexOrThrow("razorpay_signature")),
          verificationStatus = VerificationStatus.valueOf(
            c.getString(c.getColumnIndexOrThrow("verification_status"))
          ),
          transactionStatus = TransactionStatus.valueOf(
            c.getString(c.getColumnIndexOrThrow("transaction_status"))
          ),
          createdAt = c.getLong(c.getColumnIndexOrThrow("created_timestamp")),
          updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_timestamp"))
        )
      )
    }
  }
  
  return payments
}
```

## Payment Signature Verification

Store verification logic in the database repository:

```kotlin
fun verifyAndStorePayment(
  orderId: String,
  paymentId: String,
  signature: String,
  apiKeyManager: ApiKeyManager
): Boolean {
  try {
    val secret = apiKeyManager.getApiSecret() ?: return false
    
    // Verify signature: HMAC-SHA256(orderId|paymentId, secret) == signature
    val message = "$orderId|$paymentId"
    val expectedSignature = generateSignature(message, secret)
    
    val isValid = expectedSignature == signature
    
    // Update verification status in database
    updateVerificationStatus(
      orderId,
      if (isValid) VerificationStatus.VERIFIED else VerificationStatus.FAILED,
      isValid
    )
    
    return isValid
  } catch (e: Exception) {
    Log.e("PaymentDB", "Error verifying signature", e)
    return false
  }
}

private fun generateSignature(message: String, secret: String): String {
  val mac = javax.crypto.Mac.getInstance("HmacSHA256")
  val key = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256")
  mac.init(key)
  val bytes = mac.doFinal(message.toByteArray())
  return bytes.joinToString("") { "%02x".format(it) }
}
```

## Webhook Handling (Optional)

If implementing Razorpay webhooks for payment verification:

```kotlin
fun handleRazorpayWebhook(webhookData: String): Boolean {
  try {
    val json = JSONObject(webhookData)
    
    val event = json.getString("event")
    val payload = json.getJSONObject("payload")
    
    return when (event) {
      "payment.authorized" -> handlePaymentAuthorized(payload)
      "payment.failed" -> handlePaymentFailed(payload)
      else -> false
    }
  } catch (e: Exception) {
    Log.e("Webhook", "Error handling webhook", e)
    return false
  }
}

private fun handlePaymentAuthorized(payload: JSONObject): Boolean {
  val orderId = payload.getJSONObject("payment").getString("order_id")
  val paymentId = payload.getJSONObject("payment").getString("id")
  
  // Update payment status
  updateVerificationStatus(orderId, VerificationStatus.VERIFIED, true)
  
  // Activate subscription
  activateUserSubscription(orderId)
  
  return true
}
```

## Data Retention & Privacy

### Secure Data Handling

1. Never log full payment IDs or signatures
2. Hash sensitive data before storing
3. Use database encryption for sensitive columns
4. Implement automatic data cleanup

```kotlin
fun cleanupOldPaymentRecords(daysToKeep: Int = 90) {
  val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000)
  
  database.inAppPaymentTable.delete(
    "created_timestamp < ? AND transaction_status = ?",
    arrayOf(cutoffTime.toString(), "FAILED")
  )
}
```

## Testing Database Operations

```kotlin
class InAppPaymentRepositoryTest {
  
  @Test
  fun testStoreRazorpayPayment() {
    val record = repository.storeRazorpayPayment(
      userId = "user123",
      planId = "premium",
      amountInRupees = 599,
      razorpayOrderId = "order_123",
      razorpayPaymentId = "pay_456",
      razorpaySignature = "sig_789"
    )
    
    assertTrue(record > 0)
    
    val retrieved = repository.getPaymentByOrderId("order_123")
    assertNotNull(retrieved)
    assertEquals("premium", retrieved?.planId)
    assertEquals(59900, retrieved?.amountInPaisa)  // 599 * 100
    assertEquals("INR", retrieved?.currencyCode)
  }
  
  @Test
  fun testGetUserSubscription() {
    repository.storeRazorpayPayment(
      userId = "user123",
      planId = "pro",
      amountInRupees = 299,
      razorpayOrderId = "order_456",
      razorpayPaymentId = "pay_789",
      razorpaySignature = "sig_123"
    )
    
    val plan = repository.getUserSubscriptionPlan("user123")
    assertNotNull(plan)
    assertEquals("pro", plan?.id)
    assertEquals(299, plan?.amountInRupees)
  }
  
  @Test
  fun testAmountInPaisaStorage() {
    val record = repository.storeRazorpayPayment(
      userId = "user123",
      planId = "basic",
      amountInRupees = 99,
      razorpayOrderId = "order_789",
      razorpayPaymentId = "pay_123",
      razorpaySignature = "sig_456"
    )
    
    val retrieved = repository.getPaymentByOrderId("order_789")
    assertEquals(9900, retrieved?.amountInPaisa)  // Verify conversion to paisa
  }
}
```

## Migration Checklist

- [ ] Create database migration file
- [ ] Add new columns to InAppPaymentTable
- [ ] Update data models
- [ ] Create repository methods for storing/querying
- [ ] Implement signature verification
- [ ] Add payment history queries
- [ ] Setup webhook handling (if applicable)
- [ ] Test all database operations
- [ ] Verify INR currency always stored
- [ ] Test data cleanup procedures

## References

- Database location: `app/src/main/java/org/thoughtcrime/securesms/database/`
- Existing payment table patterns
- Signal database migration framework
