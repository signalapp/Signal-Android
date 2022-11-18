package org.thoughtcrime.securesms.devicetransfer;

import androidx.annotation.NonNull;

import static org.thoughtcrime.securesms.devicetransfer.SetupStep.VERIFY;

/**
 * State representation of the current {@link SetupStep} in the setup flow and
 * the SAS if one has been provided.
 */
public final class DeviceSetupState {

  private final SetupStep currentSetupStep;
  private final int       authenticationCode;

  public DeviceSetupState() {
    this(SetupStep.INITIAL, 0);
  }

  public DeviceSetupState(@NonNull SetupStep currentSetupStep, int authenticationCode) {
    this.currentSetupStep   = currentSetupStep;
    this.authenticationCode = authenticationCode;
  }

  public @NonNull SetupStep getCurrentSetupStep() {
    return currentSetupStep;
  }

  public int getAuthenticationCode() {
    return authenticationCode;
  }

  public @NonNull DeviceSetupState updateStep(@NonNull SetupStep currentSetupStep) {
    return new DeviceSetupState(currentSetupStep, this.authenticationCode);
  }

  public @NonNull DeviceSetupState updateVerificationRequired(int authenticationCode) {
    return new DeviceSetupState(VERIFY, authenticationCode);
  }
}
