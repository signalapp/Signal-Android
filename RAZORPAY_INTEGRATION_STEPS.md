# Step-by-Step Razorpay Integration Guide

Complete implementation guide to integrate Razorpay payment gateway into Signal-Android while replacing existing payment systems.

## Prerequisites

1. Razorpay Account with API keys
2. API Key and API Secret from Razorpay Dashboard
3. Android API 21 or higher
4. Signal-Android project setup

## Phase 1: Setup & Configuration (Done)

The following files have been created:
- `RazorpayConfig.kt` - Configuration and plan definitions
- `ApiKeyManager.kt` - Secure API key storage
- `RazorpayKeySetupFragment.kt` - API key input UI

## Phase 2: Core Billing Implementation (Done)

The following files have been created:
- `RazorpayBillingApiImpl.kt` - Implements BillingApi interface
- `RazorpayPaymentHandler.kt` - Payment handling logic
- `RazorpayPaymentActivity.kt` - Payment checkout UI

## Phase 3: Payment Flow Integration (Done)

The following files have been created:
- `RazorpayPaymentFlowViewModel.kt` - State management
- `RazorpayPaymentIntegration.kt` - Helper methods

## Phase 4: Integration with Existing Signal Code

### Step 1: Update BillingFactory

**File:** `lib/billing/src/main/java/org/signal/billing/BillingFactory.kt`

```kotlin
// Find this method
fun getBillingApi(context: Context): BillingApi {
  // Old code
  return when {
    playServicesAvailable(context) -> GooglePlayBillingApiImpl(context)
    else -> BillingApi.Empty
  }
}

// Replace with
fun getBillingApi(context: Context): BillingApi {
  return RazorpayBillingApiImpl(context)
}
```

### Step 2: Update CheckoutFlowActivity

**File:** `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/subscription/CheckoutFlowActivity.kt`

Add Razorpay payment handling:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  
  // ... existing code ...
  
  // Initialize Razorpay payment system
  RazorpayPaymentIntegration.initialize(this)
}

// Handle payment button click
paymentButton.setOnClickListener {
  val planId = selectedPlanId // Get selected plan
  val customerId = getCurrentUserId()
  
  RazorpayPaymentIntegration.initiatePayment(
    this,
    planId,
    customerId
  ) { success, message ->
    if (success) {
      showSuccess("Payment successful")
      updateUserSubscription(planId)
    } else {
      showError(message)
    }
  }
}

// Handle activity result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  super.onActivityResult(requestCode, resultCode, data)
  
  RazorpayPaymentIntegration.handleActivityResult(
    this,
    requestCode,
    resultCode,
    data
  ) { success, paymentId ->
    if (success) {
      Log.d("Checkout", "Payment completed: $paymentId")
      completeCheckout(paymentId)
    }
  }
}
```

### Step 3: Update InAppPaymentCheckoutDelegate

**File:** `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/subscription/donate/InAppPaymentCheckoutDelegate.kt`

Replace Google Play billing with Razorpay:

```kotlin
class InAppPaymentCheckoutDelegate(
  private val context: Context,
  private val viewModel: InAppPaymentViewModel
) {
  
  fun launchCheckout(activity: Activity, planId: String) {
    // Initialize Razorpay
    RazorpayPaymentIntegration.initialize(context)
    
    // Check if API keys configured
    if (!RazorpayPaymentIntegration.isConfigured(context)) {
      // Show API key setup
      showApiKeySetup(activity)
      return
    }
    
    // Initiate payment
    val customerId = viewModel.userId
    RazorpayPaymentIntegration.initiatePayment(
      activity,
      planId,
      customerId
    ) { success, message ->
      viewModel.setPaymentResult(success, message)
    }
  }
  
  private fun showApiKeySetup(activity: Activity) {
    val fragment = RazorpayKeySetupFragment.newInstance()
    fragment.show(
      (activity as? FragmentActivity)?.supportFragmentManager ?: return,
      "razorpay_setup"
    )
  }
}
```

### Step 4: Update UpgradeToPaidTierBottomSheet

**File:** `app/src/main/java/org/thoughtcrime/securesms/billing/upgrade/UpgradeToPaidTierBottomSheet.kt`

Display INR prices only and use Razorpay:

```kotlin
@Composable
fun UpgradeToPaidTierBottomSheet(
  onPlanSelected: (String) -> Unit
) {
  val plans = RazorpayConfig.ALL_PLANS
  
  LazyColumn {
    items(plans.size) { index ->
      val plan = plans[index]
      
      PlanCard(
        title = plan.name,
        price = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees),
        description = plan.description,
        features = plan.features,
        onSelect = { onPlanSelected(plan.id) }
      )
    }
  }
}

@Composable
fun PlanCard(
  title: String,
  price: String,
  description: String,
  features: List<String>,
  onSelect: () -> Unit
) {
  Card {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall)
      Text(price, style = MaterialTheme.typography.displaySmall) // Shows ₹99, ₹299, etc.
      Text(description)
      
      features.forEach { feature ->
        Text("• $feature")
      }
      
      Button(onClick = onSelect) {
        Text("Select Plan")
      }
    }
  }
}
```

### Step 5: Update Subscription/MessageBackupsFlowViewModel

**File:** `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/subscription/MessageBackupsFlowViewModel.kt`

Add Razorpay payment integration:

```kotlin
class MessageBackupsFlowViewModel(
  private val context: Context
) : ViewModel() {
  
  private val _selectedPlan = MutableStateFlow<String?>(null)
  val selectedPlan: StateFlow<String?> = _selectedPlan
  
  fun selectPlan(planId: String) {
    _selectedPlan.value = planId
    Log.d("BackupVM", "Selected plan: $planId")
  }
  
  fun initiatePayment(activity: Activity) {
    val planId = _selectedPlan.value ?: return
    val customerId = SignalStore.account.requireE164()
    
    RazorpayPaymentIntegration.initiatePayment(
      activity,
      planId,
      customerId
    ) { success, message ->
      if (success) {
        onPaymentSuccess(planId)
      } else {
        onPaymentError(message)
      }
    }
  }
  
  private fun onPaymentSuccess(planId: String) {
    // Update user's subscription tier
    updateUserSubscriptionTier(planId)
  }
  
  private fun onPaymentError(message: String) {
    Log.e("BackupVM", "Payment error: $message")
  }
}
```

### Step 6: Add API Key Setup on First Payment

**File:** Update any Activity that handles payments

```kotlin
override fun onResume() {
  super.onResume()
  
  // Check if Razorpay is configured
  if (!RazorpayPaymentIntegration.isConfigured(this)) {
    // Show one-time setup dialog
    showApiKeySetupDialog()
  }
}

private fun showApiKeySetupDialog() {
  MaterialAlertDialogBuilder(this)
    .setTitle("Configure Payment Gateway")
    .setMessage("Razorpay API keys need to be configured to process payments.")
    .setPositiveButton("Configure") { _, _ ->
      RazorpayPaymentIntegration.showApiKeySetup(supportFragmentManager)
    }
    .setNegativeButton("Cancel", null)
    .show()
}
```

## Phase 5: Update Layout Files

### Create Razorpay Key Setup Layout

Create `res/layout/fragment_razorpay_key_setup.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical"
  android:padding="16dp">

  <TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/razorpay_setup_title"
    android:textSize="20sp"
    android:textStyle="bold"
    android:layout_marginBottom="16dp" />

  <TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/razorpay_setup_instructions"
    android:textSize="14sp"
    android:layout_marginBottom="16dp" />

  <com.google.android.material.textfield.TextInputLayout
    android:id="@+id/api_key_input"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    android:hint="API Key">

    <com.google.android.material.textfield.TextInputEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content" />
  </com.google.android.material.textfield.TextInputLayout>

  <com.google.android.material.textfield.TextInputLayout
    android:id="@+id/api_secret_input"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    android:hint="API Secret">

    <com.google.android.material.textfield.TextInputEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:inputType="textPassword" />
  </com.google.android.material.textfield.TextInputLayout>

  <LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="end">

    <Button
      android:id="@+id/test_button"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:text="@string/test" />

    <Button
      android:id="@+id/save_button"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:text="@string/save" />
  </LinearLayout>
</LinearLayout>
```

### Add String Resources

Add to `res/values/strings.xml`:

```xml
<string name="razorpay_setup_title">Configure Razorpay Payment</string>
<string name="razorpay_setup_instructions">Enter your Razorpay API credentials from https://dashboard.razorpay.com/</string>
<string name="razorpay_currency">₹ INR</string>
<string name="razorpay_payment_successful">Payment successful. Subscription activated.</string>
<string name="razorpay_payment_failed">Payment failed. Please try again.</string>
```

## Phase 6: Gradle Dependencies

Add to `app/build.gradle`:

```gradle
dependencies {
  // Razorpay SDK (when ready to integrate actual checkout)
  // implementation 'com.razorpay:checkout:1.6.33'
  
  // For encrypted shared preferences (already in Signal)
  implementation 'androidx.security:security-crypto:1.1.0-alpha06'
  
  // For UI components (already in Signal)
  implementation 'com.google.android.material:material:1.x.x'
}
```

## Phase 7: AndroidManifest.xml Updates

Add to `AndroidManifest.xml`:

```xml
<application ...>
  <!-- Existing activities -->
  
  <!-- Razorpay Payment Activity -->
  <activity
    android:name="org.thoughtcrime.securesms.payments.razorpay.RazorpayPaymentActivity"
    android:exported="false"
    android:theme="@style/Theme.Signal" />
</application>

<!-- Add permission if needed for payments -->
<uses-permission android:name="android.permission.INTERNET" />
```

## Testing Checklist

### Unit Tests

```kotlin
class RazorpayConfigTest {
  @Test
  fun testPlanFormatting() {
    val plan = RazorpayConfig.PLAN_PREMIUM
    val formatted = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
    assertEquals("₹599", formatted)
  }
  
  @Test
  fun testApiKeyValidation() {
    assertTrue(RazorpayConfig.isValidApiKey("key_1234567890123456"))
    assertFalse(RazorpayConfig.isValidApiKey("short"))
  }
}
```

### Integration Tests

1. Test API key setup flow
2. Test payment initiation
3. Test payment success handling
4. Test payment failure handling
5. Test INR currency display

### Manual Tests

- [ ] Configure Razorpay API keys
- [ ] Select a payment plan
- [ ] Verify price shows in ₹ only
- [ ] Complete payment flow
- [ ] Verify subscription updated
- [ ] Test dark mode
- [ ] Test on multiple devices
- [ ] Test network failure scenario

## Troubleshooting

### API Key Not Persisting

Check:
1. EncryptedSharedPreferences initialization
2. File permissions
3. Device has keystore

### Payment Not Launching

Check:
1. API keys configured
2. RazorpayPaymentActivity exported in manifest
3. Intent extras passed correctly

### INR Not Displaying

Check:
1. Using `RazorpayConfig.formatAmountForDisplay()`
2. No other currency formatting code
3. Database storing amounts in rupees

## Rollback Plan

If issues occur:

1. Revert changes to `BillingFactory.kt`
2. Restore original `CheckoutFlowActivity.kt`
3. Revert `InAppPaymentCheckoutDelegate.kt`
4. Remove Razorpay payment files
5. Test with original billing system

## Next Steps

1. [ ] Configure Razorpay API keys
2. [ ] Run all tests
3. [ ] Deploy to staging
4. [ ] User acceptance testing
5. [ ] Production deployment

## Support

For issues or questions:
1. Check RAZORPAY_INR_CURRENCY_GUIDE.md for currency-related questions
2. Review error logs in Logcat
3. Check Razorpay API documentation
