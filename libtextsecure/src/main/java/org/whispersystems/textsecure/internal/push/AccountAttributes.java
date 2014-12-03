/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.whispersystems.textsecure.internal.push;

public class AccountAttributes {

  private String  signalingKey;
  private boolean supportsSms;
  private int     registrationId;
  private boolean fetchesMessages;

  public AccountAttributes(String signalingKey, boolean supportsSms, int registrationId) {
    this(signalingKey, supportsSms, registrationId, false);
  }

  public AccountAttributes(String signalingKey, boolean supportsSms, int registrationId, boolean fetchesMessages) {
    this.signalingKey    = signalingKey;
    this.supportsSms     = supportsSms;
    this.registrationId  = registrationId;
    this.fetchesMessages = fetchesMessages;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public boolean isSupportsSms() {
    return supportsSms;
  }

  public int getRegistrationId() {
    return registrationId;
  }
}
