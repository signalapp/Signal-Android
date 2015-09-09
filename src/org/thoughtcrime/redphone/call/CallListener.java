///*
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package org.thoughtcrime.redphone.call;
//
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.net.Uri;
//import android.util.Log;
//
//import org.thoughtcrime.redphone.Constants;
//import org.thoughtcrime.redphone.RedPhone;
//import org.thoughtcrime.redphone.RedPhoneService;
//import org.thoughtcrime.redphone.directory.NumberFilter;
//import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
//import org.thoughtcrime.redphone.ui.RedPhoneChooser;
//
///**
// * CallListener inspects broadcast events for outgoing
// * calls.  We intercept them and act on them if:
// *
// * 1) The number has an '*' appended to it.
// * 2) The number is present in the RedPhone directory.
// *
// * @author Moxie Marlinspike
// *
// */
//public class CallListener extends BroadcastReceiver {
//
//  public  static final String IGNORE_SUFFIX       = "###";
//  public  static final String INTENT_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER";
//  private static final String REDPHONE_SUFFIX     = "*";
//
//  private void redirectToRedphone(Context context, Intent intent, String phoneNumber) {
//    setResultData(null);
//
//    intent.setClass(context, RedPhoneService.class);
//    intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
//
//    String destNumber = phoneNumber.substring(0, phoneNumber.length()-1);
//    intent.putExtra(Constants.REMOTE_NUMBER, destNumber );
//    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    context.startService(intent);
//
//    Intent activityIntent = new Intent();
//    activityIntent.setClass(context, RedPhone.class);
//    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    context.startActivity(activityIntent);
//  }
//
//  private void redirectToOpportunisticRedphone(Context context, Intent intent, String phoneNumber) {
//    setResultData(null);
//
//    intent.setClass(context, RedPhoneChooser.class);
//    intent.putExtra(Constants.REMOTE_NUMBER, phoneNumber);
//    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    context.startActivity(intent);
//  }
//
//  @Override
//  public void onReceive(Context context, Intent intent) {
//    String phoneNumber        = intent.getExtras().getString(INTENT_PHONE_NUMBER);
//    NumberFilter numberFilter = NumberFilter.deserializeFromFile(context);
//
//    if( phoneNumber == null )
//      return;
//
//    if (phoneNumber.endsWith(IGNORE_SUFFIX)) {
//      phoneNumber = phoneNumber.substring(0, phoneNumber.length() - IGNORE_SUFFIX.length());
//      intent.removeExtra(INTENT_PHONE_NUMBER);
//      intent.removeExtra("android.phone.extra.ORIGINAL_URI");
//
//      intent.putExtra(INTENT_PHONE_NUMBER, phoneNumber);
//      intent.putExtra("android.phone.extra.ORIGINAL_URI", "tel:" + Uri.encode(phoneNumber));
//
//      setResultData(phoneNumber);
//    } else if (phoneNumber.endsWith(REDPHONE_SUFFIX)) {
//      Log.w("CallListener", "Redirecting to RedPhone dialer...");
//      redirectToRedphone(context, intent, phoneNumber);
//    } else if (ApplicationPreferencesActivity.getPromptUpgradePreference(context) &&
//               numberFilter.containsNumber(context, phoneNumber)                  &&
//               !CallChooserCache.getInstance().isRecentInsecureChoice(phoneNumber))
//    {
//      Log.w("CallListener", "Redirecting to RedPhone opportunistic dialog...");
//      redirectToOpportunisticRedphone(context, intent, phoneNumber);
//    }
//  }
//}
