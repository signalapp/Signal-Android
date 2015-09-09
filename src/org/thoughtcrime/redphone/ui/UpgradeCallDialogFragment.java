///*
// * Copyright (C) 2013 Open Whisper Systems
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
//package org.thoughtcrime.redphone.ui;
//
//import android.app.AlertDialog;
//import android.app.Dialog;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Bundle;
//import android.support.v4.app.DialogFragment;
//import android.text.SpannableStringBuilder;
//import android.text.Spanned;
//import android.text.style.AbsoluteSizeSpan;
//import android.view.ContextThemeWrapper;
//import android.widget.Button;
//
//import org.thoughtcrime.redphone.Constants;
//import org.thoughtcrime.redphone.R;
//import org.thoughtcrime.redphone.RedPhone;
//import org.thoughtcrime.redphone.RedPhoneService;
//import org.thoughtcrime.redphone.call.CallChooserCache;
//import org.thoughtcrime.redphone.call.CallListener;
//
//
//public class UpgradeCallDialogFragment extends DialogFragment {
//
//  private final String number;
//  public UpgradeCallDialogFragment(final String number) {
//    this.number = number;
//  }
//
//  @Override
//  public Dialog onCreateDialog(Bundle savedInstanceState) {
//
//    final AlertDialog.Builder builder;
//    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB) {
//      builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.RedPhone_Light_Dialog));
//    } else {
//      builder = new AlertDialog.Builder(getActivity(), R.style.RedPhone_Light_Dialog);
//    }
//
//    builder.setIcon(R.drawable.red_call);
//
//    final String upgradeString = getActivity().getResources().getString(R.string.RedPhoneChooser_upgrade_to_redphone);
//    SpannableStringBuilder titleBuilder = new SpannableStringBuilder(upgradeString);
//    titleBuilder.setSpan(new AbsoluteSizeSpan(20, true), 0, upgradeString.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
//    builder.setTitle(titleBuilder);
//
//    //builder.setMessage(R.string.RedPhoneChooser_this_contact_also_uses_redphone_would_you_like_to_upgrade_to_a_secure_call);
//
//    builder.setPositiveButton(R.string.RedPhoneChooser_secure_call, new DialogInterface.OnClickListener() {
//      public void onClick(DialogInterface dialog, int which) {
//        Intent intent = new Intent(getActivity(), RedPhoneService.class);
//        intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
//        intent.putExtra(Constants.REMOTE_NUMBER, number);
//        getActivity().startService(intent);
//
//        Intent activityIntent = new Intent();
//        activityIntent.setClass(getActivity(), RedPhone.class);
//        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(activityIntent);
//
//        getActivity().finish();
//      }
//    });
//
//    builder.setNegativeButton(R.string.RedPhoneChooser_insecure_call, new DialogInterface.OnClickListener() {
//      public void onClick(DialogInterface dialog, int which) {
//        CallChooserCache.getInstance().addInsecureChoice(number);
//
//        Intent intent = new Intent("android.intent.action.CALL",
//            Uri.fromParts("tel", number + CallListener.IGNORE_SUFFIX,
//                          null));
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(intent);
//        getActivity().finish();
//      }
//    });
//
//    AlertDialog alert = builder.create();
//
//    alert.setOnShowListener(new DialogInterface.OnShowListener() {
//      @Override
//      public void onShow(DialogInterface dialog) {
//        ((AlertDialog) dialog).setOnCancelListener(new DialogInterface.OnCancelListener() {
//          @Override
//          public void onCancel(DialogInterface dialogInterface) {
//            getActivity().finish();
//          }
//        });
//
//        ((AlertDialog) dialog).setOnDismissListener(new DialogInterface.OnDismissListener() {
//          @Override
//          public void onDismiss(DialogInterface dialogInterface) {
//            getActivity().finish();
//          }
//        });
//        Button positiveButton = ((AlertDialog) dialog)
//            .getButton(AlertDialog.BUTTON_POSITIVE);
//
//        Button negativeButton = ((AlertDialog) dialog)
//            .getButton(AlertDialog.BUTTON_NEGATIVE);
//      }
//    });
//
//    return alert;
//  }
//
//}
