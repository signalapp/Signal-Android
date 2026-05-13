# Razorpay INR Currency Integration Guide

This guide explains how to integrate INR (Indian Rupee) currency display throughout Signal-Android while using Razorpay as the exclusive payment gateway.

## Overview

All payments in Signal-Android now display exclusively in Indian Rupees (₹). No other currencies are shown. This simplifies the user experience for the Indian market.

## Currency Constants

The base currency code and symbol are defined in `RazorpayConfig.kt`:

```kotlin
object Currency {
  const val CODE = "INR"
  const val SYMBOL = "₹"
  const val DISPLAY_NAME = "Indian Rupee"
}
```

## Amount Storage & Conversion

### Internal Storage
- Amounts are stored in **paisa** (1 INR = 100 paisa) in the database
- Razorpay API requires amounts in paisa
- Example: ₹99 = 9900 paisa

### Display to Users
- Display amounts as whole rupee numbers with ₹ symbol
- Never show paisa to end users
- Format: ₹99, ₹299, ₹599, etc.

### Conversion Formula
```
Display Amount (₹) = Amount in Paisa ÷ 100
Amount in Paisa = Display Amount (₹) × 100
```

## Implementation in Payment Plans

All payment plans are defined in `RazorpayConfig.kt`:

```kotlin
val PLAN_BASIC = PaymentPlan(
  id = "basic",
  name = "Basic",
  amountInRupees = 99,  // Display amount
  amountInPaisa = 9900   // API amount (auto-calculated)
)
```

## Updating Existing Payment UI

### Step 1: Replace Currency Display

**Before:**
```kotlin
val fiatMoney = FiatMoney(
  amount = 99L,
  currency = Currency.getInstance("USD")
)
textView.text = "${fiatMoney.currency.symbol}${fiatMoney.amount}"
```

**After:**
```kotlin
val amountInRupees = 99
textView.text = RazorpayConfig.formatAmountForDisplay(amountInRupees)
// Output: ₹99
```

### Step 2: Update Subscription Tier Display

In `UpgradeToPaidTierBottomSheet.kt` or similar:

```kotlin
// Old approach
val price = subscription.price // May be in different currency

// New approach
val plan = RazorpayConfig.getPlanById(planId)
priceText.text = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
```

### Step 3: Remove Multi-Currency Selection

**Remove all currency selector UI:**
- Delete currency selection dropdown
- Remove currency toggle buttons
- Delete "Choose Currency" dialogs

**Remove imports for:**
- Multi-currency libraries
- Currency conversion utilities
- PaymentMethodsActivity (Google Pay)
- StripePaymentActivity
- PayPalPaymentActivity

## Integration Points

### 1. Payment Plan Selection Sheet

```kotlin
// Show payment plans
val plans = RazorpayConfig.ALL_PLANS
plans.forEach { plan ->
  val displayAmount = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
  println("${plan.name}: $displayAmount/month")
}
```

### 2. Checkout Confirmation

```kotlin
// Confirm payment before checkout
val amount = RazorpayConfig.formatAmountForDisplay(selectedPlan.amountInRupees)
val message = "Confirm payment of $amount for ${selectedPlan.name}"
```

### 3. Receipt/Invoice Display

```kotlin
// Show successful payment
val receipt = """
  Order Confirmed
  Plan: ${plan.name}
  Amount: ${RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)}
  Transaction ID: $paymentId
  Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())}
""".trimIndent()
```

## Database Schema Updates

### InAppPaymentTable Changes

Add these columns to track Razorpay payments:

```sql
ALTER TABLE inapp_payment ADD COLUMN razorpay_order_id TEXT;
ALTER TABLE inapp_payment ADD COLUMN razorpay_payment_id TEXT;
ALTER TABLE inapp_payment ADD COLUMN razorpay_signature TEXT;
ALTER TABLE inapp_payment ADD COLUMN amount_in_paisa INTEGER;
ALTER TABLE inapp_payment ADD COLUMN currency_code TEXT DEFAULT 'INR';
```

Example query:
```sql
INSERT INTO inapp_payment (
  razorpay_order_id,
  razorpay_payment_id,
  amount_in_paisa,
  currency_code
) VALUES (
  'order_123456',
  'pay_456789',
  9900,  -- ₹99
  'INR'
);
```

## Code Examples

### Example 1: Display Plan in UI

```kotlin
val plan = RazorpayConfig.PLAN_PREMIUM
val text = """
  ${plan.name} Plan
  ${RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)} per month
  
  Features:
  ${plan.features.joinToString("\n") { "• $it" }}
""".trimIndent()
```

### Example 2: Handle Payment Result

```kotlin
when (result) {
  is BillingPurchaseResult.Success -> {
    val amountPaid = RazorpayConfig.formatAmountForDisplay(99)
    Toast.makeText(
      context,
      "Payment of $amountPaid successful",
      Toast.LENGTH_SHORT
    ).show()
  }
}
```

### Example 3: Format in Preference Display

```kotlin
// In settings/preferences
val currentPlan = RazorpayConfig.getPlanById(userPlanId)
preferenceScreen.summary = """
  Active: ${currentPlan?.name}
  Cost: ${RazorpayConfig.formatAmountForDisplay(currentPlan?.amountInRupees ?: 0)}/month
""".trimIndent()
```

## Files to Update

### Core Payment Files
- [ ] `UpgradeToPaidTierBottomSheet.kt` - Show INR prices only
- [ ] `MessageBackupsFlowViewModel.kt` - Use INR currency
- [ ] `InAppPaymentCheckoutDelegate.kt` - Handle Razorpay flow
- [ ] `CheckoutFlowActivity.kt` - Redirect to Razorpay
- [ ] `Subscription.kt` - Force INR currency

### UI Preference Files
- [ ] `SettingsActivity.kt` - Show INR prices in settings
- [ ] `DonationViewModel.kt` - Display INR amounts
- [ ] All subscription UI fragments - Display INR only

### Remove These Files/Code
- [ ] Google Play billing related code (keep interface only)
- [ ] GooglePayComponent.kt
- [ ] StripePaymentInProgressFragment.kt
- [ ] PayPalPaymentInProgressFragment.kt
- [ ] Multi-currency converter utilities
- [ ] Currency selection dialogs

## Testing Currency Display

### Manual Testing

1. **Plan Display Test**
   - [ ] Open subscription/donation screen
   - [ ] Verify all plans show amounts in ₹ only
   - [ ] Verify no other currency appears

2. **Payment Flow Test**
   - [ ] Select a plan
   - [ ] Verify price shown is in ₹
   - [ ] Verify confirmation shows ₹
   - [ ] Complete payment
   - [ ] Verify receipt shows ₹

3. **Settings Display Test**
   - [ ] Open settings
   - [ ] Verify subscription status shows ₹
   - [ ] Verify price is displayed correctly

### Automated Testing

```kotlin
// Test that only INR is used
@Test
fun testCurrencyIsAlwaysINR() {
  val plan = RazorpayConfig.PLAN_PREMIUM
  val formatted = RazorpayConfig.formatAmountForDisplay(plan.amountInRupees)
  
  assertTrue(formatted.startsWith("₹"))
  assertFalse(formatted.contains("$"))
  assertFalse(formatted.contains("€"))
}

// Test amount formatting
@Test
fun testAmountFormatting() {
  val formatted = RazorpayConfig.formatAmountForDisplay(99)
  assertEquals("₹99", formatted)
}

// Test paisa conversion
@Test
fun testPaisaConversion() {
  val plan = RazorpayConfig.PLAN_BASIC
  assertEquals(99, plan.amountInRupees)
  assertEquals(9900, plan.amountInPaisa)
}
```

## Common Issues & Solutions

### Issue: Other currencies still showing

**Solution:** Search for "USD", "$", "EUR", "€" in codebase and remove those references.

```bash
grep -r "USD\|EUR\|GBP" app/src --include="*.kt"
```

### Issue: Amount showing in paisa

**Solution:** Always divide by 100 before display

```kotlin
// Wrong
textView.text = amountInPaisa.toString() // Shows 9900 instead of 99

// Correct
textView.text = RazorpayConfig.formatAmountForDisplay(amountInPaisa / 100)
```

### Issue: Missing ₹ symbol

**Solution:** Use `RazorpayConfig.formatAmountForDisplay()` instead of manual formatting

```kotlin
// Wrong
textView.text = "₹" + amount // Might show incorrect formatting

// Correct
textView.text = RazorpayConfig.formatAmountForDisplay(amount)
```

## Migration Checklist

- [ ] All payment plans defined with INR amounts in `RazorpayConfig`
- [ ] All UI screens updated to use `RazorpayConfig.formatAmountForDisplay()`
- [ ] Database schema updated with Razorpay fields
- [ ] Google Pay/Stripe/PayPal code removed
- [ ] Currency selection UI removed
- [ ] No other currencies display in any payment-related UI
- [ ] All amounts stored as integers (paisa) in database
- [ ] All amounts displayed as rupee amounts with ₹ symbol
- [ ] Tested on multiple devices and screen sizes
- [ ] Dark mode tested
- [ ] All payment flows tested end-to-end

## References

- `RazorpayConfig.kt` - All currency configuration
- `RazorpayPaymentIntegration.kt` - Helper methods
- [Razorpay API Docs](https://razorpay.com/docs/api/)
- [Indian Rupee (INR) on Wikipedia](https://en.wikipedia.org/wiki/Indian_rupee)
