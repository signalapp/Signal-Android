package org.signal.contactstest

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

class AccountAuthenticatorService : Service() {
  companion object {
    private var accountAuthenticator: AccountAuthenticatorImpl? = null
  }

  override fun onBind(intent: Intent): IBinder? {
    return if (intent.action == AccountManager.ACTION_AUTHENTICATOR_INTENT) {
      getOrCreateAuthenticator().iBinder
    } else {
      null
    }
  }

  @Synchronized
  private fun getOrCreateAuthenticator(): AccountAuthenticatorImpl {
    if (accountAuthenticator == null) {
      accountAuthenticator = AccountAuthenticatorImpl(this)
    }
    return accountAuthenticator as AccountAuthenticatorImpl
  }

  private class AccountAuthenticatorImpl(context: Context) : AbstractAccountAuthenticator(context) {
    override fun addAccount(response: AccountAuthenticatorResponse, accountType: String, authTokenType: String, requiredFeatures: Array<String>, options: Bundle): Bundle? {
      return null
    }

    override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account, options: Bundle): Bundle? {
      return null
    }

    override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? {
      return null
    }

    override fun getAuthToken(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle? {
      return null
    }

    override fun getAuthTokenLabel(authTokenType: String): String? {
      return null
    }

    override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account, features: Array<String>): Bundle? {
      return null
    }

    override fun updateCredentials(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle? {
      return null
    }
  }
}
