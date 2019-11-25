package org.thoughtcrime.securesms.util.task;

import android.content.Context;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
* AsyncTask class to initialize the security of a conversation in background
**/
public class InitializeSecurityAsyncTask extends AsyncTask<Recipient, Void, boolean[]> {


  private long threadId;

  private boolean currentSecureText;

  private boolean currentIsDefaultSms;

  private SettableFuture<Boolean> future;

  private WeakReference<Context> contextWeakReference;

  private WeakReference<ConversationActivity> activityWeakReference;

  private String tag;

  public InitializeSecurityAsyncTask(long threadId, boolean currentSecureText, boolean currentIsDefaultSms, SettableFuture<Boolean> future, WeakReference<Context> contextWeakReference, WeakReference<ConversationActivity> activityWeakReference, String tag) {
    this.threadId = threadId;
    this.currentSecureText = currentSecureText;
    this.currentIsDefaultSms = currentIsDefaultSms;
    this.future = future;
    this.contextWeakReference = contextWeakReference;
    this.activityWeakReference = activityWeakReference;
    this.tag = tag;
  }

  @Override
  protected boolean[] doInBackground(Recipient... params) {
    Context context = contextWeakReference.get();
    Recipient recipient = params[0].resolve();
    Log.i(tag, "Resolving registered state...");
    RecipientDatabase.RegisteredState registeredState;

    if (recipient.isPushGroup()) {
      Log.i(tag, "Push group recipient...");
      registeredState = RecipientDatabase.RegisteredState.REGISTERED;
    } else {
      Log.i(tag, "Checking through resolved recipient");
      registeredState = recipient.resolve().getRegistered();
    }

    Log.i(tag, "Resolved registered state: " + registeredState);
    boolean signalEnabled = TextSecurePreferences.isPushRegistered(context);

    if (registeredState == RecipientDatabase.RegisteredState.UNKNOWN) {
      try {
        Log.i(tag, "Refreshing directory for user: " + recipient.getId().serialize());
        registeredState = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
      } catch (IOException e) {
        Log.w(tag, e);
      }
    }

    Log.i(tag, "Returning registered state...");
    return new boolean[]{registeredState == RecipientDatabase.RegisteredState.REGISTERED && signalEnabled,
        Util.isDefaultSmsProvider(context)};
  }

  @Override
  protected void onPostExecute(boolean[] result) {
    if (result[0] != currentSecureText || result[1] != currentIsDefaultSms) {
      Log.i(tag, "onPostExecute() handleSecurityChange: " + result[0] + " , " + result[1]);
      activityWeakReference.get().handleSecurityChange(result[0], result[1]);
    }
    future.set(true);
    activityWeakReference.get().onSecurityUpdated();
  }
}