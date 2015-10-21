package org.thoughtcrime.securesms.audio;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * @author      Stefan "frostymarvelous" Froelich <stefan d0t froelich At whisppa DoT com>
 */
public class AudioAttachmentServer implements Runnable {

  private static final String TAG = AudioAttachmentServer.class.getSimpleName();

  private final Context      context;
  private final MasterSecret masterSecret;
  private final Attachment   attachment;
  private final ServerSocket socket;
  private final int          port;

  private volatile boolean isRunning;

  public AudioAttachmentServer(Context context, MasterSecret masterSecret, Attachment attachment)
      throws IOException
  {
    try {
      this.context      = context;
      this.masterSecret = masterSecret;
      this.attachment   = attachment;
      this.socket       = new ServerSocket(0, 0, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
      this.port         = socket.getLocalPort();

      this.socket.setSoTimeout(5000);
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
  }

  public Uri getUri() {
    return Uri.parse(String.format("http://127.0.0.1:%d", port));
  }

  public void start() {
    isRunning = true;
    new Thread(this).start();
  }

  public void stop() {
    isRunning = false;
  }

  @Override
  public void run() {
    while (isRunning) {
      try {
        Socket client = socket.accept();

        if (client != null) {
          StreamToMediaPlayerTask task = new StreamToMediaPlayerTask(client);

          if (task.processRequest()) {
            task.execute();
          }
        }

      } catch (SocketTimeoutException e) {
        Log.w(TAG, e);
      } catch (IOException e) {
        Log.e(TAG, "Error connecting to client", e);
      }
    }

    Log.d(TAG, "Proxy interrupted. Shutting down.");
  }


  private class StreamToMediaPlayerTask {

    private final Socket client;

    private long       cbSkip;
    private Properties parameters;
    private Properties request;
    private Properties requestHeaders;
//    private String filePath;

    public StreamToMediaPlayerTask(Socket client) {
      this.client = client;
    }

    public boolean processRequest() throws IOException {
            InputStream is         = client.getInputStream();
      final int         bufferSize = 8192;
            byte[]      buffer     = new byte[bufferSize];
            int         splitByte  = 0;
            int         readLength = 0;

      {
        int read = is.read(buffer, 0, bufferSize);
        while (read > 0) {
          readLength += read;
          splitByte = findHeaderEnd(buffer, readLength);
          if (splitByte > 0)
            break;
          read = is.read(buffer, readLength, bufferSize - readLength);
        }
      }

      // Create a BufferedReader for parsing the header.
      ByteArrayInputStream hbis = new ByteArrayInputStream(buffer, 0, readLength);
      BufferedReader       hin  = new BufferedReader(new InputStreamReader(hbis));

      request        = new Properties();
      parameters     = new Properties();
      requestHeaders = new Properties();

      try {
        decodeHeader(hin, request, parameters, requestHeaders);
      } catch (InterruptedException e1) {
        Log.e(TAG, "Exception: " + e1.getMessage());
        e1.printStackTrace();
      }

      for (Map.Entry<Object, Object> e : requestHeaders.entrySet()) {
        Log.i(TAG, "Header: " + e.getKey() + " : " + e.getValue());
      }

      String range = requestHeaders.getProperty("range");

      if (range != null) {
        Log.i(TAG, "range is: " + range);
        range = range.substring(6);
        int charPos = range.indexOf('-');
        if (charPos > 0) {
          range = range.substring(0, charPos);
        }
        cbSkip = Long.parseLong(range);
        Log.i(TAG, "range found!! " + cbSkip);
      }

      if(!request.get("method").equals("GET")) {
        Log.e(TAG, "Only GET is supported");
        return false;
      }

//      filePath = request.getProperty("uri");

      return true;
    }

    protected void execute() throws IOException {
      InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri());
      long        fileSize    = attachment.getSize();

      String headers = "";
      if (cbSkip > 0) {// It is a seek or skip request if there's a Range
        // header
        headers += "HTTP/1.1 206 Partial Content\r\n";
        headers += "Content-Type: " + attachment.getContentType() + "\r\n";
        headers += "Accept-Ranges: bytes\r\n";
        headers += "Content-Length: " + (fileSize - cbSkip) + "\r\n";
        headers += "Content-Range: bytes " + cbSkip + "-" + (fileSize - 1) + "/" + fileSize + "\r\n";
        headers += "Connection: Keep-Alive\r\n";
        headers += "\r\n";
      } else {
        headers += "HTTP/1.1 200 OK\r\n";
        headers += "Content-Type: " + attachment.getContentType() + "\r\n";
        headers += "Accept-Ranges: bytes\r\n";
        headers += "Content-Length: " + fileSize + "\r\n";
        headers += "Connection: Keep-Alive\r\n";
        headers += "\r\n";
      }

      Log.i(TAG, "headers: " + headers);

      OutputStream output = null;
      byte[] buff = new byte[64 * 1024];
      try {
        output = new BufferedOutputStream(client.getOutputStream(), 32 * 1024);
        output.write(headers.getBytes());

        inputStream.skip(cbSkip);
//        dataSource.skipFully(data, cbSkip);//try to skip as much as possible

        // Loop as long as there's stuff to send and client has not closed
        int cbRead;
        while (!client.isClosed() && (cbRead = inputStream.read(buff, 0, buff.length)) != -1) {
          output.write(buff, 0, cbRead);
        }
      }
      catch (SocketException socketException) {
        Log.e(TAG, "SocketException() thrown, proxy client has probably closed. This can exit harmlessly");
      }
      catch (Exception e) {
        Log.e(TAG, "Exception thrown from streaming task:");
        Log.e(TAG, e.getClass().getName() + " : " + e.getLocalizedMessage());
      }

      // Cleanup
      try {
        if (output != null) {
          output.close();
        }
        client.close();
      }
      catch (IOException e) {
        Log.e(TAG, "IOException while cleaning up streaming task:");
        Log.e(TAG, e.getClass().getName() + " : " + e.getLocalizedMessage());
        e.printStackTrace();
      }
    }

    /**
     * Find byte index separating header from body. It must be the last byte of
     * the first two sequential new lines.
     **/
    private int findHeaderEnd(final byte[] buf, int rlen) {
      int splitbyte = 0;
      while (splitbyte + 3 < rlen) {
        if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n'
            && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n')
          return splitbyte + 4;
        splitbyte++;
      }
      return 0;
    }


    /**
     * Decodes the sent headers and loads the data into java Properties' key -
     * value pairs
     **/
    private void decodeHeader(BufferedReader in, Properties pre,
                              Properties parms, Properties header) throws InterruptedException {
      try {
        // Read the request line
        String inLine = in.readLine();
        if (inLine == null)
          return;
        StringTokenizer st = new StringTokenizer(inLine);
        if (!st.hasMoreTokens())
          Log.e(TAG,
                "BAD REQUEST: Syntax error. Usage: GET /example/file.html");

        String method = st.nextToken();
        pre.put("method", method);

        if (!st.hasMoreTokens())
          Log.e(TAG,
                "BAD REQUEST: Missing URI. Usage: GET /example/file.html");

        String uri = st.nextToken();

        // Decode parameters from the URI
        int qmi = uri.indexOf('?');
        if (qmi >= 0) {
          decodeParms(uri.substring(qmi + 1), parms);
          uri = decodePercent(uri.substring(0, qmi));
        } else
          uri = decodePercent(uri);

        // If there's another token, it's protocol version,
        // followed by HTTP headers. Ignore version but parse headers.
        // NOTE: this now forces header names lowercase since they are
        // case insensitive and vary by client.
        if (st.hasMoreTokens()) {
          String line = in.readLine();
          while (line != null && line.trim().length() > 0) {
            int p = line.indexOf(':');
            if (p >= 0)
              header.put(line.substring(0, p).trim().toLowerCase(),
                         line.substring(p + 1).trim());
            line = in.readLine();
          }
        }

        pre.put("uri", uri);
      } catch (IOException ioe) {
        Log.e(TAG,
              "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
      }
    }

    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
     * Properties. NOTE: this doesn't support multiple identical keys due to the
     * simplicity of Properties -- if you need multiples, you might want to
     * replace the Properties with a Hashtable of Vectors or such.
     */
    private void decodeParms(String parms, Properties p)
        throws InterruptedException {
      if (parms == null)
        return;

      StringTokenizer st = new StringTokenizer(parms, "&");
      while (st.hasMoreTokens()) {
        String e = st.nextToken();
        int sep = e.indexOf('=');
        if (sep >= 0)
          p.put(decodePercent(e.substring(0, sep)).trim(),
                decodePercent(e.substring(sep + 1)));
      }
    }

    /**
     * Decodes the percent encoding scheme. <br/>
     * For example: "an+example%20string" -> "an example string"
     */
    private String decodePercent(String str) throws InterruptedException {
      try {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < str.length(); i++) {
          char c = str.charAt(i);
          switch (c) {
            case '+':
              sb.append(' ');
              break;
            case '%':
              sb.append((char) Integer.parseInt(
                  str.substring(i + 1, i + 3), 16));
              i += 2;
              break;
            default:
              sb.append(c);
              break;
          }
        }
        return sb.toString();
      } catch (Exception e) {
        Log.e(TAG, "BAD REQUEST: Bad percent-encoding.");
        return null;
      }
    }
  }
}