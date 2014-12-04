package com.codebutler.android_websockets;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.BasicNameValuePair;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class WebSocketClient {
  private static final String TAG = "WebSocketClient";
  private static final boolean ENFORCE_SSL = true;

  private URI mURI;
  private Listener mListener;
  private Socket mSocket;
  private Thread mThread;
  private HandlerThread mHandlerThread;
  private Handler mHandler;
  private List<BasicNameValuePair> mExtraHeaders;
  private HybiParser mParser;
  private boolean mConnected;
  private WakeLock mWakeLock;
  private final TrustManager[] mTrustManagers;

  private final Object mSendLock = new Object();

  private static TrustManager[] sTrustManagers;

  private static void setTrustManagers(TrustManager[] tm) {
    sTrustManagers = tm;
  }

  public WebSocketClient(URI uri, Listener listener, List<BasicNameValuePair> extraHeaders, WakeLock wakelock, TrustManager[] trustManagers) {
    mURI = uri;
    mListener = listener;
    mExtraHeaders = extraHeaders;
    mConnected = false;
    mParser = new HybiParser(this, wakelock);

    mHandlerThread = new HandlerThread("websocket-thread");
    mHandlerThread.start();
    mHandler = new Handler(mHandlerThread.getLooper());
    mWakeLock = wakelock;
    mTrustManagers = trustManagers;
  }

  public Listener getListener() {
    return mListener;
  }

  public void connect() {
    if (mThread != null && mThread.isAlive()) {
      return;
    }

    mThread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          if (mWakeLock != null) synchronized (mWakeLock) {
            mWakeLock.acquire();
          }
          int port = (mURI.getPort() != -1) ? mURI.getPort() : ((mURI.getScheme().equals("wss") || mURI.getScheme().equals("https")) ? 443 : 80);

          String path = TextUtils.isEmpty(mURI.getPath()) ? "/" : mURI.getPath();
          if (!TextUtils.isEmpty(mURI.getQuery())) {
            path += "?" + mURI.getQuery().replaceAll("\\+", "%2B");
          }

          String originScheme = mURI.getScheme().equals("wss") ? "https" : "http";
          URI origin = new URI(originScheme, "//" + mURI.getHost(), null);

          SSLContext context = SSLContext.getInstance("TLS");
          context.init(null, mTrustManagers, null);

          SocketFactory factory = (ENFORCE_SSL || mURI.getScheme().equals("wss") || mURI.getScheme().equals("https")) ? context.getSocketFactory() : SocketFactory.getDefault();

          try {
            mSocket = factory.createSocket(mURI.getHost(), port);
          } catch (Exception e) {
            Log.w(TAG, e);
            mListener.onError(e);
            return;
          }

          PrintWriter out = new PrintWriter(mSocket.getOutputStream());
          out.print("GET " + path + " HTTP/1.1\r\n");
          out.print("Upgrade: websocket\r\n");
          out.print("Connection: Upgrade\r\n");
          out.print("Host: " + mURI.getHost() + "\r\n");
          out.print("Origin: " + origin.toString() + "\r\n");
          out.print("Sec-WebSocket-Key: " + createSecret() + "\r\n");
          out.print("Sec-WebSocket-Version: 13\r\n");
          if (mExtraHeaders != null) {
            for (NameValuePair pair : mExtraHeaders) {
              out.print(String.format("%s: %s\r\n", pair.getName(), pair.getValue()));
            }
          }
          out.print("\r\n");
          out.flush();

          HybiParser.HappyDataInputStream stream = new HybiParser.HappyDataInputStream(mSocket.getInputStream());

          // Read HTTP response status line.
          StatusLine statusLine = parseStatusLine(readLine(stream));
          if (statusLine == null) {
            throw new HttpException("Received no reply from server.");
          } else if (statusLine.getStatusCode() != HttpStatus.SC_SWITCHING_PROTOCOLS) {
            throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
          }

          // Read HTTP response headers.
          String line;
          while (!TextUtils.isEmpty(line = readLine(stream))) {
            Header header = parseHeader(line);
            if (header.getName().equals("Sec-WebSocket-Accept")) {
              // FIXME: Verify the response...
            }
          }

          mListener.onConnect();

          mConnected = true;
          if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
          }
          // Now decode websocket frames.
          mParser.start(stream);

        } catch (EOFException ex) {
          Log.w(TAG, "WebSocket EOF!", ex);
          mListener.onDisconnect(0, "EOF");
          mConnected = false;

        } catch (SSLException ex) {
          // Connection reset by peer
          Log.w(TAG, "Websocket SSL error!", ex);
          mListener.onError(ex);
          mConnected = false;

        } catch (Exception ex) {
          mListener.onError(ex);
          mConnected = false;
        } finally {
          if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.setReferenceCounted(false);
            mWakeLock.release();
            mWakeLock.setReferenceCounted(true);
          }
        }
      }
    });
    mThread.start();
  }

  public void disconnect() {
    if (mSocket != null) {
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          try {
            mSocket.close();
            mSocket = null;
            mConnected = false;
          } catch (IOException ex) {
            Log.w(TAG, "Error while disconnecting", ex);
            mListener.onError(ex);
          }
        }
      });
    }
  }

  public void ping(String message) {
    mParser.ping(message);
  }

  public void ping() {
    mParser.ping("");
  }

  public void send(String data) {
    sendFrame(mParser.frame(data));
  }

  public void send(byte[] data) {
    sendFrame(mParser.frame(data));
  }

  public boolean isConnected() {
    return mConnected;
  }

  private StatusLine parseStatusLine(String line) {
    if (TextUtils.isEmpty(line)) {
      return null;
    }
    return BasicLineParser.parseStatusLine(line, new BasicLineParser());
  }

  private Header parseHeader(String line) {
    return BasicLineParser.parseHeader(line, new BasicLineParser());
  }

  // Can't use BufferedReader because it buffers past the HTTP data.
  private String readLine(HybiParser.HappyDataInputStream reader) throws IOException {
    int readChar = reader.read();
    if (readChar == -1) {
      return null;
    }
    StringBuilder string = new StringBuilder("");
    while (readChar != '\n') {
      if (readChar != '\r') {
        string.append((char) readChar);
      }

      readChar = reader.read();
      if (readChar == -1) {
        return null;
      }
    }
    return string.toString();
  }

  private String createSecret() {
    byte[] nonce = new byte[16];
    for (int i = 0; i < 16; i++) {
      nonce[i] = (byte) (Math.random() * 256);
    }
    return Base64.encodeToString(nonce, Base64.DEFAULT).trim();
  }

  void sendFrame(final byte[] frame) {
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          synchronized (mSendLock) {
            if (mWakeLock != null) synchronized (mWakeLock) {
              mWakeLock.acquire();
            }
            OutputStream outputStream = mSocket.getOutputStream();
            outputStream.write(frame);
            outputStream.flush();
          }
        } catch (IOException e) {
          mListener.onError(e);
          mConnected = false;
          if (mWakeLock != null) synchronized (mWakeLock) {
            mWakeLock.setReferenceCounted(false);
          }
        } finally {
          if (mWakeLock != null) synchronized (mWakeLock) {
            if (mWakeLock.isHeld()) {
              mWakeLock.release();
              mWakeLock.setReferenceCounted(true);
            }
          }
        }
      }
    });
  }

  public interface Listener {
    public void onConnect();

    public void onPong(String message);

    public void onMessage(String message);

    public void onMessage(byte[] data);

    public void onDisconnect(int code, String reason);

    public void onError(Exception error);
  }

  private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, sTrustManagers, null);
    return context.getSocketFactory();
  }
}
