package org.thoughtcrime.redphone.call;

import android.util.Log;

import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.signaling.signals.ServerSignal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignalManager {

  private final ExecutorService queue = Executors.newSingleThreadExecutor();

  private final SignalingSocket signalingSocket;
  private final SessionDescriptor sessionDescriptor;
  private final CallStateListener callStateListener;

  private volatile boolean interrupted = false;

  public SignalManager(CallStateListener callStateListener,
                       SignalingSocket signalingSocket,
                       SessionDescriptor sessionDescriptor)
  {
    this.callStateListener = callStateListener;
    this.signalingSocket   = signalingSocket;
    this.sessionDescriptor = sessionDescriptor;

    this.queue.execute(new SignalListenerTask());
  }

//  public void sendBusySignal(String remoteNumber, final long sessionId) {
//    Log.w("SignalManager", "Queuing busy signal...");
//    queue.execute(new Runnable() {
//      public void run() {
//        try {
//          Log.w("SignalManager", "Sending busy signal...");
//          signalingSocket.setBusy(sessionId);
//        } catch (SignalingException se) {
//          Log.w("SignalManager", se);
//        }
//      }
//    });
//
//    interrupted = true;
//  }

  public void terminate() {
    Log.w("SignalManager", "Queuing hangup signal...");
    queue.execute(new Runnable() {
      public void run() {
        Log.w("SignalManager", "Sending hangup signal...");
        signalingSocket.setHangup(sessionDescriptor.sessionId);
        signalingSocket.close();
        queue.shutdownNow();
      }
    });

    interrupted = true;
  }

  private class SignalListenerTask implements Runnable {
    public void run() {
      Log.w("SignalManager", "Running Signal Listener...");

      try {
        while (!interrupted) {
          if (signalingSocket.waitForSignal())
            break;
        }

        Log.w("SignalManager", "Signal Listener Running, interrupted: " + interrupted);

        if (!interrupted) {
          ServerSignal signal = signalingSocket.readSignal();
          long sessionId      = sessionDescriptor.sessionId;

          if      (signal.isHangup(sessionId))  callStateListener.notifyCallDisconnected();
          else if (signal.isRinging(sessionId)) callStateListener.notifyCallRinging();
          else if (signal.isBusy(sessionId))    callStateListener.notifyBusy();
          else if (signal.isKeepAlive())        Log.w("CallManager", "Received keep-alive...");

          signalingSocket.sendOkResponse();
        }

        interrupted = false;
        queue.execute(this);
      } catch (SignalingException e) {
        Log.w("CallManager", e);
        callStateListener.notifyCallDisconnected();
      }
    }
  }
}
