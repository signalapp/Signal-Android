///*
// * Copyright (C) 2015 Open Whisper Systems
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
//package org.thoughtcrime.redphone.signaling;
//
//import android.content.Context;
//
//import org.thoughtcrime.redphone.signaling.signals.CreateAccountSignal;
//import org.thoughtcrime.redphone.signaling.signals.VerifyAccountSignal;
//import org.thoughtcrime.securesms.BuildConfig;
//
//
//public class AccountCreationSocket extends SignalingSocket {
//
//  public AccountCreationSocket(Context context, String localNumber, String password)
//      throws SignalingException
//  {
//    super(context, BuildConfig.REDPHONE_MASTER_HOST, 31337, localNumber, password, null);
//  }
//
//  public void createAccount(boolean voice)
//      throws SignalingException, AccountCreationException, RateLimitExceededException
//  {
//    sendSignal(new CreateAccountSignal(localNumber, password, voice));
//    SignalResponse response = readSignalResponse();
//
//    switch (response.getStatusCode()) {
//      case 200: return;
//      case 413: throw new RateLimitExceededException("Rate limit exceeded.");
//      default:  throw new AccountCreationException("Account creation failed: " +
//                                                   response.getStatusCode());
//    }
//  }
//
//  public void verifyAccount(String challenge, String key)
//      throws SignalingException, AccountCreationException, RateLimitExceededException
//  {
//    sendSignal(new VerifyAccountSignal(localNumber, password, challenge, key));
//    SignalResponse response = readSignalResponse();
//
//    switch (response.getStatusCode()) {
//      case 200: return;
//      case 413: throw new RateLimitExceededException("Verify rate exceeded!");
//      default: throw new AccountCreationException("Account verification failed: " +
//                                                  response.getStatusCode());
//    }
//  }
//
//}
