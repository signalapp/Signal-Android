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

import android.content.Context;
import android.util.Log;

import org.privatechats.redphone.audio.AudioException;
import org.privatechats.redphone.audio.CallAudioManager;
import org.privatechats.redphone.crypto.SecureRtpSocket;
import org.privatechats.redphone.crypto.zrtp.MasterSecret;
import org.privatechats.redphone.crypto.zrtp.ZRTPInitiatorSocket;
import org.privatechats.redphone.network.RtpSocket;
import org.privatechats.redphone.signaling.LoginFailedException;
import org.privatechats.redphone.signaling.NetworkConnector;
import org.privatechats.redphone.signaling.NoSuchUserException;
import org.privatechats.redphone.signaling.OtpCounterProvider;
import org.privatechats.redphone.signaling.ServerMessageException;
import org.privatechats.redphone.signaling.SessionInitiationFailureException;
import org.privatechats.redphone.signaling.SignalingException;
import org.privatechats.redphone.signaling.SignalingSocket;
import org.privatechats.securesms.BuildConfig;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Call Manager for the coordination of outgoing calls.  It initiates
 * signaling, negotiates ZRTP, and kicks off the call audio manager.
 *
 * @author Moxie Marlinspike
 *
 */
public class InitiatingCallManager extends CallManager {

  private static final String TAG = InitiatingCallManager.class.getSimpleName();

  private final String localNumber;
  private final String password;
  private final byte[] zid;

  public InitiatingCallManager(Context context, CallStateListener callStateListener,
                               String localNumber, String password,
                               String remoteNumber, byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "InitiatingCallManager Thread");
    this.localNumber    = localNumber;
    this.password       = password;
    this.zid            = zid;
  }

  @Override
  public void run() {
    try {
      callStateListener.notifyCallConnecting();

      signalingSocket = new SignalingSocket(context, BuildConfig.REDPHONE_RELAY_HOST,
                                            31337, localNumber, password,
                                            OtpCounterProvider.getInstance());

      sessionDescriptor = signalingSocket.initiateConnection(remoteNumber);

      int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                           sessionDescriptor.getFullServerName(),
                                           sessionDescriptor.relayPort).makeConnection();

      InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                              sessionDescriptor.relayPort);

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));

      zrtpSocket    = new ZRTPInitiatorSocket(context, secureSocket, zid, remoteNumber);

      processSignals();

      callStateListener.notifyWaitingForResponder();

      super.run();
    } catch (NoSuchUserException nsue) {
      Log.w(TAG, nsue);
      callStateListener.notifyNoSuchUser();
    } catch (ServerMessageException ife) {
      Log.w(TAG, ife);
      callStateListener.notifyServerMessage(ife.getMessage());
    } catch (LoginFailedException lfe) {
      Log.w(TAG, lfe);
      callStateListener.notifyLoginFailed();
    } catch (SignalingException | SessionInitiationFailureException se) {
      Log.w(TAG, se);
      callStateListener.notifyServerFailure();
    } catch (SocketException e) {
      Log.w(TAG, e);
      callStateListener.notifyCallDisconnected();
    } catch( RuntimeException e ) {
      Log.e(TAG, "Died with unhandled exception!");
      Log.w(TAG, e);
      callStateListener.notifyClientFailure();
    }
  }

  @Override
  protected void runAudio(DatagramSocket socket, String remoteIp, int remotePort,
                          MasterSecret masterSecret, boolean muteEnabled)
      throws SocketException, AudioException
  {
    this.callAudioManager = new CallAudioManager(socket, remoteIp, remotePort,
                                                 masterSecret.getInitiatorSrtpKey(),
                                                 masterSecret.getInitiatorMacKey(),
                                                 masterSecret.getInitiatorSrtpSalt(),
                                                 masterSecret.getResponderSrtpKey(),
                                                 masterSecret.getResponderMacKey(),
                                                 masterSecret.getResponderSrtpSailt());
    this.callAudioManager.setMute(muteEnabled);
    this.callAudioManager.start(context);
  }
}
