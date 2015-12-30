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

package org.thoughtcrime.redphone.call;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.redphone.audio.AudioException;
import org.thoughtcrime.redphone.audio.CallAudioManager;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.crypto.zrtp.MasterSecret;
import org.thoughtcrime.redphone.crypto.zrtp.ZRTPResponderSocket;
import org.thoughtcrime.redphone.network.RtpSocket;
import org.thoughtcrime.redphone.signaling.LoginFailedException;
import org.thoughtcrime.redphone.signaling.NetworkConnector;
import org.thoughtcrime.redphone.signaling.OtpCounterProvider;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SessionInitiationFailureException;
import org.thoughtcrime.redphone.signaling.SessionStaleException;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * CallManager responsible for coordinating incoming calls.
 *
 * @author Moxie Marlinspike
 *
 */
public class ResponderCallManager extends CallManager {

  private static final String TAG = ResponderCallManager.class.getSimpleName();

  private final String localNumber;
  private final String password;
  private final byte[] zid;

  private int answer = 0;

  private SessionDescriptor sessionDescriptor;

  public ResponderCallManager(Context context, CallStateListener callStateListener,
                              String remoteNumber, String localNumber,
                              String password, SessionDescriptor sessionDescriptor,
                              byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "ResponderCallManager Thread");
    this.localNumber       = localNumber;
    this.password          = password;
    this.sessionDescriptor = sessionDescriptor;
    this.zid               = zid;
  }

  @Override
  public void run() {
    try {
      synchronized (this.initLock) {
        if (this.terminated) return;
        Log.d(TAG, "run");

        SignalingSocket signalingSocket = new SignalingSocket(context,
                                                              sessionDescriptor.getFullServerName(),
                                                              31337,
                                                              localNumber, password,
                                                              OtpCounterProvider.getInstance());

        signalingSocket.setRinging(sessionDescriptor.sessionId);
        callStateListener.notifyCallFresh();

        processSignals(signalingSocket, sessionDescriptor);
      }

      if (!waitForAnswer()) {
        return;
      }

      synchronized (this.initLock) {
        if (this.terminated) return;
        int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                             sessionDescriptor.getFullServerName(),
                                             sessionDescriptor.relayPort).makeConnection();

        InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                                sessionDescriptor.relayPort);

        SecureRtpSocket secureSocket = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));
        zrtpSocket = new ZRTPResponderSocket(context, secureSocket, zid, remoteNumber, sessionDescriptor.version <= 0);

        callStateListener.notifyConnectingtoInitiator();
      }

      super.run();

    } catch (SignalingException | SessionInitiationFailureException se) {
      Log.w(TAG, se);
      callStateListener.notifyServerFailure();
    } catch (SessionStaleException e) {
      Log.w(TAG, e);
      callStateListener.notifyCallStale();
    } catch (LoginFailedException lfe) {
      Log.w(TAG, lfe);
      callStateListener.notifyLoginFailed();
    } catch (SocketException e) {
      Log.w(TAG, e);
      callStateListener.notifyCallDisconnected();
    } catch( RuntimeException e ) {
      Log.e(TAG, "Died unhandled with exception!");
      Log.w(TAG, e);
      callStateListener.notifyClientFailure();
    }
  }

  public synchronized void answer(boolean answer) {
    this.answer = (answer ? 1 : 2);
    notifyAll();
  }

  private synchronized boolean waitForAnswer() {
    try {
      while (answer == 0)
        wait();
    } catch (InterruptedException ie) {
      throw new IllegalArgumentException(ie);
    }

    return this.answer == 1;
  }

  @Override
  public void terminate() {
    synchronized (this) {
      if (answer == 0) {
        answer(false);
      }
    }

    super.terminate();
  }

  @Override
  protected void runAudio(DatagramSocket socket, String remoteIp, int remotePort,
                          MasterSecret masterSecret, boolean muteEnabled)
      throws SocketException, AudioException
  {
    this.callAudioManager = new CallAudioManager(socket, remoteIp, remotePort,
                                                 masterSecret.getResponderSrtpKey(),
                                                 masterSecret.getResponderMacKey(),
                                                 masterSecret.getResponderSrtpSailt(),
                                                 masterSecret.getInitiatorSrtpKey(),
                                                 masterSecret.getInitiatorMacKey(),
                                                 masterSecret.getInitiatorSrtpSalt());
    this.callAudioManager.setMute(muteEnabled);
    this.callAudioManager.start(context);
  }

}
