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

package org.privatechats.redphone.signaling;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.privatechats.redphone.network.LowLatencySocketConnector;
import org.privatechats.redphone.signaling.signals.BusySignal;
import org.privatechats.redphone.signaling.signals.HangupSignal;
import org.privatechats.redphone.signaling.signals.InitiateSignal;
import org.privatechats.redphone.signaling.signals.RingingSignal;
import org.privatechats.redphone.signaling.signals.ServerSignal;
import org.privatechats.redphone.signaling.signals.Signal;
import org.privatechats.redphone.util.LineReader;
import org.privatechats.securesms.util.JsonUtils;
import org.whispersystems.textsecure.api.push.TrustStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * A socket that speaks the signaling protocol with a whisperswitch.
 *
 * The signaling protocol is very similar to a RESTful HTTP API, where every
 * request yields a corresponding response, and authorization is done through
 * an Authorization header.
 *
 * Like SIP, however, both endpoints are simultaneously server and client, issuing
 * requests and responses to each-other.
 *
 * Connections are persistent, and the signaling connection
 * for any ongoing call must remain open, otherwise the call will drop.
 *
 * @author Moxie Marlinspike
 *
 */

public class SignalingSocket {

  private static final String TAG = SignalingSocket.class.getSimpleName();

  protected static final int    PROTOCOL_VERSION = 1;

  private   final Context context;
  private   final Socket socket;

  protected final LineReader         lineReader;
  protected final OutputStream       outputStream;
  protected final String             localNumber;
  protected final String             password;
  protected final OtpCounterProvider counterProvider;

  private boolean connectionAttemptComplete;

  public SignalingSocket(Context context, String host, int port,
                         String localNumber, String password,
                         OtpCounterProvider counterProvider)
      throws SignalingException
  {
    try {
      this.context                   = context.getApplicationContext();
      this.connectionAttemptComplete = false;
      this.socket                    = constructSSLSocket(context, host, port);
      this.outputStream              = this.socket.getOutputStream();
      this.lineReader                = new LineReader(socket.getInputStream());
      this.localNumber               = localNumber;
      this.password                  = password;
      this.counterProvider           = counterProvider;
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  private Socket constructSSLSocket(Context context, String host, int port)
      throws SignalingException
  {
    try {
      TrustManager[] trustManagers = getTrustManager(new RedPhoneTrustStore(context));
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagers, null);

      return timeoutHackConnect(sslContext.getSocketFactory(), host, port);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Socket timeoutHackConnect(SSLSocketFactory sslSocketFactory, String host, int port)
      throws IOException
  {
    InetAddress[] addresses      = InetAddress.getAllByName(host);
    Socket stagedSocket          = LowLatencySocketConnector.connect(addresses, port);

    Log.w(TAG, "Connected to: " + stagedSocket.getInetAddress().getHostAddress());

    SocketConnectMonitor monitor = new SocketConnectMonitor(stagedSocket);

    monitor.start();

    Socket result = sslSocketFactory.createSocket(stagedSocket, host, port, true);

    synchronized (this) {
      this.connectionAttemptComplete = true;
      notify();

      if (result.isConnected()) return result;
      else                      throw new IOException("Socket timed out before " +
                                                      "connection completed.");
    }
  }

  public TrustManager[] getTrustManager(TrustStore trustStore) {
    try {
      InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream();
      KeyStore keyStore            = KeyStore.getInstance("BKS");

      keyStore.load(keyStoreInputStream, trustStore.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(keyStore);

      return trustManagerFactory.getTrustManagers();
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public void close() {
    try {
      this.outputStream.close();
      this.socket.getInputStream().close();
      this.socket.close();
    } catch (IOException ioe) {}
  }

  public SessionDescriptor initiateConnection(String remoteNumber)
      throws ServerMessageException, SignalingException,
             NoSuchUserException, LoginFailedException
  {
    sendSignal(new InitiateSignal(localNumber, password,
                                  counterProvider.getOtpCounter(context),
                                  remoteNumber));

    SignalResponse response = readSignalResponse();

    try {
      switch (response.getStatusCode()) {
      case 404: throw new NoSuchUserException("No such redphone user.");
      case 402: throw new ServerMessageException(new String(response.getBody()));
      case 401: throw new LoginFailedException("Initiate threw 401");
      case 200: return JsonUtils.fromJson(response.getBody(), SessionDescriptor.class);
      default:  throw new SignalingException("Unknown response: " + response.getStatusCode());
      }
    } catch (IOException e) {
      throw new SignalingException(e);
    }
  }

  public void setRinging(long sessionId)
      throws SignalingException, SessionStaleException, LoginFailedException
  {
    sendSignal(new RingingSignal(localNumber, password,
                                 counterProvider.getOtpCounter(context),
                                 sessionId));

    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 404: throw new SessionStaleException("No such session: " + sessionId);
    case 401: throw new LoginFailedException("Ringing threw 401");
    case 200: return;
    default:  throw new SignalingException("Unknown response: " + response.getStatusCode());
    }
  }

  public void setHangup(long sessionId) {
    try {
      sendSignal(new HangupSignal(localNumber, password,
                                  counterProvider.getOtpCounter(context),
                                  sessionId));
      readSignalResponse();
    } catch (SignalingException se) {}
  }


  public void setBusy(long sessionId) throws SignalingException {
    sendSignal(new BusySignal(localNumber, password,
                              counterProvider.getOtpCounter(context),
                              sessionId));
    readSignalResponse();
  }

  public void sendOkResponse() throws SignalingException {
    try {
      this.outputStream.write("HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes());
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  public boolean waitForSignal() throws SignalingException {
    try {
      socket.setSoTimeout(1500);
      return lineReader.waitForAvailable();
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    } finally {
      try {
        socket.setSoTimeout(0);
      } catch (SocketException e) {
        Log.w("SignalingSocket", e);
      }
    }
  }

  public ServerSignal readSignal() throws SignalingException {
    try {
      SignalReader signalReader = new SignalReader(lineReader);
      String[] request            = signalReader.readSignalRequest();
      Map<String, String> headers = signalReader.readSignalHeaders();
      byte[] body                 = signalReader.readSignalBody(headers);

      return new ServerSignal(request[0].trim(), request[1].trim(), body);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  protected void sendSignal(Signal signal) throws SignalingException {
    try {
      Log.d(TAG, "Sending signal...");
      this.outputStream.write(signal.serialize().getBytes());
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  protected SignalResponse readSignalResponse() throws SignalingException {
    try {
      SignalResponseReader responseReader = new SignalResponseReader(lineReader);
      int responseCode            = responseReader.readSignalResponseCode();
      Map<String, String> headers = responseReader.readSignalHeaders();
      byte[] body                 = responseReader.readSignalBody(headers);

      return new SignalResponse(responseCode, headers, body);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  private class SocketConnectMonitor extends Thread {
    private final Socket socket;

    public SocketConnectMonitor(Socket socket) {
      this.socket           = socket;
    }

    @Override
    public void run() {
      synchronized (SignalingSocket.this) {
        try {
          if (!SignalingSocket.this.connectionAttemptComplete) SignalingSocket.this.wait(10000);
          if (!SignalingSocket.this.connectionAttemptComplete) this.socket.close();
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
    }
  }
}