/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.privatechats.redphone.call;

import org.privatechats.redphone.crypto.zrtp.SASInfo;

/**
 * An interface for those interested in receiving information
 * about the state of calls.  RedPhoneService is the notable
 * implementor, which coordinates that information and relays
 * it on to the UI.
 *
 * @author Moxie Marlinspike
 *
 */
public interface CallStateListener {
  public void notifyNoSuchUser();
  public void notifyWaitingForResponder();
  public void notifyConnectingtoInitiator();
  public void notifyServerFailure();
  public void notifyClientFailure();
  public void notifyServerMessage(String serverMessage);
  public void notifyCallDisconnected();
  public void notifyCallRinging();
  public void notifyCallConnected(SASInfo sas);
  public void notifyPerformingHandshake();
  public void notifyHandshakeFailed();
  public void notifyRecipientUnavailable();
  public void notifyBusy();
  public void notifyLoginFailed();
  public void notifyCallStale();
  public void notifyCallFresh();
  public void notifyClientError(String message);
  public void notifyCallConnecting();
}
