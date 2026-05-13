# Razorpay Payment System - Comprehensive Testing Guide

Complete testing guide for the Razorpay INR payment implementation in Signal-Android.

## Testing Strategy Overview

```
Unit Tests (60%)
├── Configuration & Validation
├── Currency Formatting
├── API Key Management
└── Database Operations

Integration Tests (25%)
├── Payment Flow
├── API Communication
├── Database Persistence
└── State Management

UI/Manual Tests (15%)
├── Visual Verification
├── User Experience
├── Device Compatibility
└── Error Scenarios
```

## Unit Testing

### 1. Configuration Tests

**File:** `app/src/test/java/org/thoughtcrime/securesms/payments/razorpay/RazorpayConfigTest.kt`

```kotlin
class RazorpayConfigTest {
  
  @Test
  fun testAllPlansHaveValidIds() {
    RazorpayConfig.ALL_PLANS.forEach { plan ->
      assertFalse(plan.id.isEmpty())
      assertTrue(plan.id.matches(Regex("^[a-z_]+$")))
    }
  }
  
  @Test
  fun testAllPlansHaveINRPricing() {
    RazorpayConfig.ALL_PLANS.forEach { plan ->
      assertTrue(plan.amountInRupees >= 0)
      assertEquals(plan.amountInRupees * 100, plan.amountInPaisa)
    }
  }
  
  @Test
  fun testPlanPrices() {
    assertEquals(0, RazorpayConfig.PLAN_FREE.amountInRupees)
    assertEquals(99, RazorpayConfig.PLAN_BASIC.amountInRupees)
    assertEquals(299, RazorpayConfig.PLAN_PRO.amountInRupees)
    assertEquals(599, RazorpayConfig.PLAN_PREMIUM.amountInRupees)
  }
  
  @Test
  fun testGetPlanById() {
    val plan = RazorpayConfig.getPlanById("premium")
    assertNotNull(plan)
    assertEquals(599, plan?.amountInRupees)
  }
  
  @Test
  fun testGetPlanByIdInvalid() {
    val plan = RazorpayConfig.getPlanById("invalid")
    assertNull(plan)
  }
  
  @Test
  fun testAmountFormatting() {
    assertEquals("₹0", RazorpayConfig.formatAmountForDisplay(0))
    assertEquals("₹99", RazorpayConfig.formatAmountForDisplay(99))
    assertEquals("₹299", RazorpayConfig.formatAmountForDisplay(299))
    assertEquals("₹599", RazorpayConfig.formatAmountForDisplay(599))
  }
  
  @Test
  fun testCurrencyConstants() {
    assertEquals("INR", RazorpayConfig.Currency.CODE)
    assertEquals("₹", RazorpayConfig.Currency.SYMBOL)
    assertEquals("Indian Rupee", RazorpayConfig.Currency.DISPLAY_NAME)
  }
}
```

### 2. API Key Validation Tests

**File:** `app/src/test/java/org/thoughtcrime/securesms/payments/razorpay/ApiKeyValidationTest.kt`

```kotlin
class ApiKeyValidationTest {
  
  @Test
  fun testValidApiKey() {
    assertTrue(RazorpayConfig.isValidApiKey("key_1234567890123456abc"))
  }
  
  @Test
  fun testApiKeyTooShort() {
    assertFalse(RazorpayConfig.isValidApiKey("short"))
  }
  
  @Test
  fun testApiKeyTooLong() {
    val tooLong = "k".repeat(65)
    assertFalse(RazorpayConfig.isValidApiKey(tooLong))
  }
  
  @Test
  fun testApiKeyInvalidCharacters() {
    assertFalse(RazorpayConfig.isValidApiKey("key_123456789@invalid#chars"))
  }
  
  @Test
  fun testApiKeyExactMinLength() {
    val minLength = "a".repeat(32)
    assertTrue(RazorpayConfig.isValidApiKey(minLength))
  }
  
  @Test
  fun testApiKeyExactMaxLength() {
    val maxLength = "a".repeat(64)
    assertTrue(RazorpayConfig.isValidApiKey(maxLength))
  }
}
```

### 3. Currency Conversion Tests

**File:** `app/src/test/java/org/thoughtcrime/securesms/payments/razorpay/CurrencyConversionTest.kt`

```kotlin
class CurrencyConversionTest {
  
  @Test
  fun testRupeeToPaisaConversion() {
    assertEquals(0, 0 * 100)
    assertEquals(9900, 99 * 100)
    assertEquals(29900, 299 * 100)
    assertEquals(59900, 599 * 100)
  }
  
  @Test
  fun testPaisaToRupeeConversion() {
    assertEquals(0, 0 / 100)
    assertEquals(99, 9900 / 100)
    assertEquals(299, 29900 / 100)
    assertEquals(599, 59900 / 100)
  }
  
  @Test
  fun testDisplayFormatNeverShowsPaisa() {
    val formatted = RazorpayConfig.formatAmountForDisplay(99)
    assertFalse(formatted.contains("."))
    assertFalse(formatted.contains("00"))
    assertTrue(formatted.startsWith("₹"))
  }
  
  @Test
  fun testAllPlansDisplayCorrectly() {
    RazorpayConfig.ALL_PLANS.forEach { plan ->
      val formatted = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
      assertTrue(formatted.startsWith("₹"))
      assertTrue(formatted.contains(plan.amountInRupees.toString()))
    }
  }
}
```

### 4. Payment Handler Tests

**File:** `app/src/test/java/org/thoughtcrime/securesms/payments/razorpay/RazorpayPaymentHandlerTest.kt`

```kotlin
class RazorpayPaymentHandlerTest {
  
  private lateinit var handler: RazorpayPaymentHandler
  private val context = mock<Context>()
  
  @Before
  fun setUp() {
    handler = RazorpayPaymentHandler(context)
  }
  
  @Test
  fun testHandleSuccessfulPayment() {
    val resultIntent = Intent().apply {
      putExtra("payment_id", "pay_123456")
      putExtra("order_id", "order_123456")
      putExtra("signature", "sig_123456")
    }
    
    val result = handler.handleActivityResult(
      RazorpayPaymentHandler.REQUEST_CODE_RAZORPAY,
      Activity.RESULT_OK,
      resultIntent
    )
    
    assertTrue(result is BillingPurchaseResult.Success)
  }
  
  @Test
  fun testHandleUserCancelled() {
    val result = handler.handleActivityResult(
      RazorpayPaymentHandler.REQUEST_CODE_RAZORPAY,
      Activity.RESULT_CANCELED,
      null
    )
    
    assertTrue(result is BillingPurchaseResult.UserCancelled)
  }
  
  @Test
  fun testHandleInvalidResultCode() {
    val result = handler.handleActivityResult(
      999, // Invalid request code
      Activity.RESULT_OK,
      null
    )
    
    assertTrue(result is BillingPurchaseResult.None)
  }
}
```

## Integration Testing

### 1. Payment Flow Integration Test

**File:** `app/src/androidTest/java/org/thoughtcrime/securesms/payments/razorpay/PaymentFlowIntegrationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class PaymentFlowIntegrationTest {
  
  @get:Rule
  val activityRule = ActivityScenarioRule(TestPaymentActivity::class.java)
  
  @Test
  fun testCompletePaymentFlow() {
    // 1. Verify API keys are configured
    val apiKeyManager = ApiKeyManager(InstrumentationRegistry.getInstrumentation().context)
    assertTrue(apiKeyManager.hasApiKeys())
    
    // 2. Select plan
    onView(withId(R.id.premium_plan_button)).perform(click())
    
    // 3. Verify plan is selected
    onView(withId(R.id.selected_plan_text))
      .check(matches(withText(containsString("Premium"))))
    
    // 4. Verify price shown in INR
    onView(withId(R.id.plan_price_text))
      .check(matches(withText(containsString("₹599"))))
    
    // 5. Initiate payment
    onView(withId(R.id.pay_button)).perform(click())
    
    // 6. Verify payment activity launched
    intended(hasComponent(RazorpayPaymentActivity::class.java.name))
  }
  
  @Test
  fun testCurrencyAlwaysINR() {
    val plans = RazorpayConfig.ALL_PLANS
    plans.forEach { plan ->
      val formatted = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
      
      // Verify INR symbol present
      assertTrue(formatted.contains("₹"))
      
      // Verify no other currency symbols
      assertFalse(formatted.contains("$"))
      assertFalse(formatted.contains("€"))
      assertFalse(formatted.contains("£"))
    }
  }
}
```

### 2. API Key Storage Integration Test

**File:** `app/src/androidTest/java/org/thoughtcrime/securesms/payments/razorpay/ApiKeyStorageIntegrationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class ApiKeyStorageIntegrationTest {
  
  private lateinit var apiKeyManager: ApiKeyManager
  private val context = InstrumentationRegistry.getInstrumentation().context
  
  @Before
  fun setUp() {
    apiKeyManager = ApiKeyManager(context)
    apiKeyManager.clearApiKeys() // Start fresh
  }
  
  @After
  fun tearDown() {
    apiKeyManager.clearApiKeys()
  }
  
  @Test
  fun testStoreAndRetrieveApiKeys() {
    val apiKey = "key_1234567890123456abc"
    val apiSecret = "secret_1234567890123456abc"
    
    apiKeyManager.storeApiKeys(apiKey, apiSecret)
    
    assertEquals(apiKey, apiKeyManager.getApiKey())
    assertEquals(apiSecret, apiKeyManager.getApiSecret())
  }
  
  @Test
  fun testApiKeysEncrypted() {
    apiKeyManager.storeApiKeys(
      "key_1234567890123456abc",
      "secret_1234567890123456abc"
    )
    
    // Verify stored in encrypted preferences
    assertTrue(apiKeyManager.hasApiKeys())
  }
  
  @Test
  fun testInvalidApiKeyRejected() {
    val invalidKey = "short"
    
    assertThrows(ApiKeyManager.ApiKeyException::class.java) {
      apiKeyManager.storeApiKeys(invalidKey, "secret_1234567890123456abc")
    }
  }
  
  @Test
  fun testClearApiKeys() {
    apiKeyManager.storeApiKeys(
      "key_1234567890123456abc",
      "secret_1234567890123456abc"
    )
    
    assertTrue(apiKeyManager.hasApiKeys())
    
    apiKeyManager.clearApiKeys()
    
    assertFalse(apiKeyManager.hasApiKeys())
  }
}
```

## UI/Manual Testing

### Test Scenarios

#### Scenario 1: Plan Selection & Display

```
Steps:
1. Open subscription/donation screen
2. Scroll through all payment plans
3. Verify each plan displays price in ₹ only
4. Tap on each plan
5. Verify plan is highlighted
6. Verify price remains displayed in ₹

Expected Results:
- All prices show as: ₹0, ₹99, ₹299, ₹599
- No decimal places shown
- ₹ symbol present on all prices
- No other currency symbols appear
- Selection is clear and visible
```

#### Scenario 2: Successful Payment Flow

```
Steps:
1. Select a payment plan
2. Tap "Proceed to Payment"
3. Verify Razorpay checkout launches
4. Complete test payment
5. Verify success message shown
6. Verify subscription activated

Expected Results:
- Razorpay interface loads
- Payment amount shows correctly in ₹
- Success confirmation appears
- App updates subscription status
- User can see receipt with ₹ currency
```

#### Scenario 3: Payment Cancellation

```
Steps:
1. Select a payment plan
2. Tap "Proceed to Payment"
3. In Razorpay checkout, press back or cancel
4. Observe app behavior

Expected Results:
- App returns to payment selection
- Error message shows: "Payment cancelled"
- No subscription charged
- User can retry payment
```

#### Scenario 4: API Key Configuration

```
Steps:
1. Launch app for first time
2. Try to select a plan
3. If API keys not configured, setup dialog shows
4. Enter API key and secret
5. Tap "Save"

Expected Results:
- Setup dialog appears clearly
- Instructions are visible
- Keys are validated before saving
- Success message appears
- Can proceed with payment
```

#### Scenario 5: Dark Mode Compatibility

```
Steps:
1. Enable system dark mode
2. Open subscription screen
3. Verify all text is readable
4. Verify currency symbols display correctly
5. Repeat with light mode

Expected Results:
- All text readable in both modes
- ₹ symbol visible in both modes
- Buttons have good contrast
- No text cutoff or overlap
```

### Device Testing

Test on devices with:
- [ ] Android 8.0 (API 26)
- [ ] Android 10.0 (API 29)
- [ ] Android 12.0 (API 31)
- [ ] Android 14.0+ (API 34+)

Screen sizes:
- [ ] Small phone (4.5 inch)
- [ ] Regular phone (6 inch)
- [ ] Large phone (6.5 inch)
- [ ] Tablet (7-10 inch)

Screen orientations:
- [ ] Portrait mode
- [ ] Landscape mode
- [ ] Rotating between modes

### Accessibility Testing

```
Checklist:
- [ ] Screen reader can read all plan prices
- [ ] Currency symbol announced correctly
- [ ] Buttons have clear labels
- [ ] Text size adjustable
- [ ] High contrast mode supported
- [ ] Touch targets are at least 48dp
- [ ] Tab order is logical
- [ ] Color not only indicator of interactivity
```

## Performance Testing

### Memory Usage Test

```kotlin
@Test
fun testMemoryUsage() {
  val runtime = Runtime.getRuntime()
  val usedMemBefore = runtime.totalMemory() - runtime.freeMemory()
  
  // Create multiple plan instances
  repeat(1000) {
    RazorpayConfig.getPlanById("premium")
  }
  
  val usedMemAfter = runtime.totalMemory() - runtime.freeMemory()
  val memoryIncrease = usedMemAfter - usedMemBefore
  
  // Memory increase should be minimal
  assertTrue(memoryIncrease < 1_000_000) // Less than 1MB
}
```

### Payment Flow Response Time

```kotlin
@Test
fun testPaymentInitiationSpeed() {
  val startTime = System.currentTimeMillis()
  
  // Initiate payment
  RazorpayPaymentIntegration.initiatePayment(
    activity,
    "premium",
    "user123"
  ) { _, _ -> }
  
  val endTime = System.currentTimeMillis()
  val duration = endTime - startTime
  
  // Should launch within 500ms
  assertTrue(duration < 500)
}
```

## Regression Testing

### Checklist Before Each Release

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Payment plans display with correct INR prices
- [ ] No other currency appears in any UI
- [ ] API key setup works correctly
- [ ] Payment flow completes successfully
- [ ] Subscription updates after payment
- [ ] Database stores payment correctly
- [ ] No memory leaks detected
- [ ] App doesn't crash on payment screens
- [ ] Dark mode works correctly
- [ ] Settings show correct plan and price
- [ ] Payment history displays correctly
- [ ] Error messages are clear
- [ ] Back button works as expected
- [ ] Network errors handled gracefully

## Test Data

### Test API Keys (Sandbox)

```
Key ID: key_test_1234567890123456
Secret: secret_test_1234567890123456
Order ID: order_test_123456789
Payment ID: pay_test_123456789
```

### Test Cards (Razorpay Sandbox)

- Visa: 4111 1111 1111 1111
- Mastercard: 5555 5555 5555 4444
- Amount: Any amount (INR)
- Date: Any future date
- CVV: Any 3 digits

## Continuous Integration

### GitHub Actions/CI Configuration

```yaml
name: Razorpay Payment Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
      
      - name: Run Integration Tests
        run: ./gradlew connectedAndroidTest
      
      - name: Check Currency Always INR
        run: grep -r "USD\|EUR\|GBP" app/src/main --include="*.kt" && exit 1 || exit 0
```

## Known Issues & Workarounds

### Issue: EMulator Payment Flow Not Working
**Workaround:** Test on real device or use Razorpay test credentials

### Issue: Encrypted SharedPreferences Fails
**Workaround:** Verify device has working keystore

### Issue: Currency Symbol Not Displaying
**Workaround:** Use Unicode escape: `\u20B9` for ₹

## Reporting & Documentation

After testing, document:
1. Test results (pass/fail)
2. Any issues found
3. Screenshots of bugs
4. Device/OS details
5. Steps to reproduce issues
6. Suggested fixes

---

Total estimated testing time: 2-3 days for full coverage
