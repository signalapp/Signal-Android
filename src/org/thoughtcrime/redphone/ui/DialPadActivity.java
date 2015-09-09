///*
// * Copyright (C) 2014 Open Whisper Systems
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
//import android.content.Intent;
//import android.os.Build;
//import android.os.Bundle;
//import android.support.v4.app.Fragment;
//import android.text.Editable;
//import android.view.KeyEvent;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.TableRow;
//import android.widget.TextView;
//import android.widget.Toast;
//
//
//import org.thoughtcrime.redphone.Constants;
//import org.thoughtcrime.redphone.RedPhone;
//import org.thoughtcrime.redphone.RedPhoneService;
//import org.thoughtcrime.redphone.dialer.DialpadKeyButton;
//import org.thoughtcrime.redphone.util.Util;
//
///**
// * Activity that displays a dial pad.
// *
// * @author Moxie Marlinspike
// *
// */
//
//public class DialPadActivity extends Fragment
//    implements DialpadKeyButton.OnPressedListener, View.OnLongClickListener, View.OnClickListener {
//
//  private EditText digitEntry;
//  private View deleteButton;
//
//  @Override
//  public void onActivityCreated(Bundle icicle) {
//    super.onActivityCreated(icicle);
//  }
//
//  @Override
//  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//    View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);
//
//    initializeDeleteButton(fragmentView);
//    initializeDigitEntry(fragmentView);
//    initializeKeyPad(fragmentView);
//    initializeCallButton(fragmentView);
//
//    return fragmentView;
//  }
//
//  @Override
//  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//    inflater.inflate(R.menu.contact_list_options_menu, menu);
//  }
//
//  private void initializeCallButton(View fragmentView) {
//    ImageView callButton = (ImageView)fragmentView.findViewById(R.id.call_button);
//    callButton.setOnClickListener(this);
//  }
//
//  private void initializeDigitEntry(View fragmentView) {
//    digitEntry = (EditText) fragmentView.findViewById(R.id.digits);
//    digitEntry.setOnClickListener(this);
//    digitEntry.setOnLongClickListener(this);
//  }
//
//  private void initializeDeleteButton(View fragmentView) {
//    deleteButton = fragmentView.findViewById(R.id.deleteButton);
//    if (deleteButton != null) {
//      deleteButton.setOnClickListener(this);
//      deleteButton.setOnLongClickListener(this);
//    }
//  }
//
//  private void initializeKeyPad(View fragmentView) {
//    int[] buttonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three, R.id.four,
//                                 R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine,
//                                 R.id.star, R.id.pound};
//
//    String[] numbers = new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#"};
//    String[] letters = new String[] {"+", "", "ABC", "DEF", "GHI","JKL", "MNO", "PQRS", "TUV", "WXYZ", "", ""};
//
//    DialpadKeyButton dialpadKey;
//    TextView numberView;
//    TextView lettersView;
//
//    for (int i = 0; i < buttonIds.length; i++) {
//      dialpadKey = (DialpadKeyButton) fragmentView.findViewById(buttonIds[i]);
//      dialpadKey.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
//                                                           TableRow.LayoutParams.MATCH_PARENT));
//      dialpadKey.setOnPressedListener(this);
//      numberView  = (TextView) dialpadKey.findViewById(R.id.dialpad_key_number);
//      lettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
//
//      numberView.setText(numbers[i]);
//      dialpadKey.setContentDescription(numbers[i]);
//
//      if (lettersView != null) {
//        lettersView.setText(letters[i]);
//      }
//    }
//
//    fragmentView.findViewById(R.id.zero).setOnLongClickListener(this);
//  }
//
//
//  @Override
//  public void onPressed(View view, boolean pressed) {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? !pressed : pressed) return;
//
//    switch (view.getId()) {
//      case R.id.one:   keyPressed(KeyEvent.KEYCODE_1);     break;
//      case R.id.two:   keyPressed(KeyEvent.KEYCODE_2);     break;
//      case R.id.three: keyPressed(KeyEvent.KEYCODE_3);     break;
//      case R.id.four:  keyPressed(KeyEvent.KEYCODE_4);     break;
//      case R.id.five:  keyPressed(KeyEvent.KEYCODE_5);     break;
//      case R.id.six:   keyPressed(KeyEvent.KEYCODE_6);     break;
//      case R.id.seven: keyPressed(KeyEvent.KEYCODE_7);     break;
//      case R.id.eight: keyPressed(KeyEvent.KEYCODE_8);     break;
//      case R.id.nine:  keyPressed(KeyEvent.KEYCODE_9);     break;
//      case R.id.zero:  keyPressed(KeyEvent.KEYCODE_0);     break;
//      case R.id.pound: keyPressed(KeyEvent.KEYCODE_POUND); break;
//      case R.id.star:  keyPressed(KeyEvent.KEYCODE_STAR);  break;
//    }
//  }
//
//  @Override
//  public boolean onLongClick(View view) {
//    Editable digits = digitEntry.getText();
//    int      id     = view.getId();
//
//    switch (id) {
//      case R.id.deleteButton: {
//        digits.clear();
//        // TODO: The framework forgets to clear the pressed
//        // status of disabled button. Until this is fixed,
//        // clear manually the pressed status. b/2133127
//        deleteButton.setPressed(false);
//        return true;
//      }
//      case R.id.zero: {
//        // Remove tentative input ('0') done by onTouch().
//        removePreviousDigitIfPossible();
//        keyPressed(KeyEvent.KEYCODE_PLUS);
//        return true;
//      }
//      case R.id.digits: {
//        digitEntry.setCursorVisible(true);
//        return false;
//      }
//    }
//    return false;
//
//  }
//
//  private void keyPressed(int keyCode) {
////    if (getView().getTranslationY() != 0) {
////      return;
////    }
//    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
//    digitEntry.onKeyDown(keyCode, event);
//
//    final int length = digitEntry.length();
//    if (length == digitEntry.getSelectionStart() && length == digitEntry.getSelectionEnd()) {
//      digitEntry.setCursorVisible(false);
//    }
//  }
//
//  private void removePreviousDigitIfPossible() {
//    final int currentPosition = digitEntry.getSelectionStart();
//
//    if (currentPosition > 0) {
//      digitEntry.setSelection(currentPosition);
//      digitEntry.getText().delete(currentPosition - 1, currentPosition);
//    }
//  }
//
//
//  @Override
//  public void onClick(View view) {
//    switch (view.getId()) {
//      case R.id.deleteButton:
//        keyPressed(KeyEvent.KEYCODE_DEL);
//        return;
//      case R.id.digits:
//        if (!(digitEntry.length() == 0)) {
//          digitEntry.setCursorVisible(true);
//        }
//        return;
//      case R.id.call_button:
//        String number = digitEntry.getText().toString();
//
//        if (Util.isEmpty(number)) {
//          Toast.makeText(getActivity(),
//                         getActivity().getString(R.string.DialPadActivity_you_must_dial_a_number_first),
//                         Toast.LENGTH_LONG).show();
//          return;
//        }
//
//        Intent intent = new Intent(getActivity(), RedPhoneService.class);
//
//        intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
//        intent.putExtra(Constants.REMOTE_NUMBER, number );
//        getActivity().startService(intent);
//
//        Intent activityIntent = new Intent(getActivity(), RedPhone.class);
//        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(activityIntent);
//
//        getActivity().finish();
//        return;
//    }
//  }
//}
