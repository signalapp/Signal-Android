package org.thoughtcrime.securesms.service;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {

  private static AccountAuthenticatorImpl accountAuthenticator = null;

  @Override
  public IBinder onBind(Intent intent) {
    if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT)) {
      return getAuthenticator().getIBinder();
    } else {
      return null;
    }
  }

  private synchronized AccountAuthenticatorImpl getAuthenticator() {
    if (accountAuthenticator == null) {
      accountAuthenticator = new AccountAuthenticatorImpl(this);
    }

    return accountAuthenticator;
  }

  private static class AccountAuthenticatorImpl extends AbstractAccountAuthenticator {

    public AccountAuthenticatorImpl(Context context) {
      super(context);
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
                             String[] requiredFeatures, Bundle options)
        throws NetworkErrorException
    {
      return null;
    }

    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
      return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
      return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
                               Bundle options) throws NetworkErrorException {
      return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
      return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
        throws NetworkErrorException {
      return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
                                    Bundle options) {
      return null;
    }
  }
}
