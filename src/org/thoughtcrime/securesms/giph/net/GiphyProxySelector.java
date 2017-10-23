package org.thoughtcrime.securesms.giph.net;


import android.os.AsyncTask;
import android.util.Log;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class GiphyProxySelector extends ProxySelector {

  private static final String TAG = GiphyProxySelector.class.getSimpleName();

  private final    List<Proxy> EMPTY = new ArrayList<>(1);
  private volatile List<Proxy> GIPHY = null;

  public GiphyProxySelector() {
    EMPTY.add(Proxy.NO_PROXY);

    if (Util.isMainThread()) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (GiphyProxySelector.this) {
            initializeGiphyProxy();
            GiphyProxySelector.this.notifyAll();
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else {
      initializeGiphyProxy();
    }
  }

  @Override
  public List<Proxy> select(URI uri) {
    if (uri.getHost().endsWith("giphy.com")) return getOrCreateGiphyProxy();
    else                                     return EMPTY;
  }

  @Override
  public void connectFailed(URI uri, SocketAddress address, IOException failure) {
    Log.w(TAG, failure);
  }

  private void initializeGiphyProxy() {
    GIPHY = new ArrayList<Proxy>(1) {{
      add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(BuildConfig.GIPHY_PROXY_HOST,
                                                           BuildConfig.GIPHY_PROXY_PORT)));
    }};
  }

  private List<Proxy> getOrCreateGiphyProxy() {
    if (GIPHY == null) {
      synchronized (this) {
        while (GIPHY == null) Util.wait(this, 0);
      }
    }

    return GIPHY;
  }

}
