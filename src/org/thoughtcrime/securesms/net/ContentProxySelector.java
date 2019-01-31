package org.thoughtcrime.securesms.net;


import android.os.AsyncTask;

import org.thoughtcrime.securesms.linkpreview.LinkPreviewDomains;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContentProxySelector extends ProxySelector {

  private static final String TAG = ContentProxySelector.class.getSimpleName();

  public static final Set<String> WHITELISTED_DOMAINS = new HashSet<>();
  static {
    WHITELISTED_DOMAINS.addAll(LinkPreviewDomains.LINKS);
    WHITELISTED_DOMAINS.addAll(LinkPreviewDomains.IMAGES);
    WHITELISTED_DOMAINS.add("giphy.com");
  }

  private final    List<Proxy> EMPTY   = new ArrayList<>(1);
  private volatile List<Proxy> CONTENT = null;

  public ContentProxySelector() {
    EMPTY.add(Proxy.NO_PROXY);

    if (Util.isMainThread()) {
      AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
        synchronized (ContentProxySelector.this) {
          initializeContentProxy();
          ContentProxySelector.this.notifyAll();
        }
      });
    } else {
      initializeContentProxy();
    }
  }

  @Override
  public List<Proxy> select(URI uri) {
    for (String domain : WHITELISTED_DOMAINS) {
      if (uri.getHost().endsWith(domain)) {
        return getOrCreateContentProxy();
      }
    }
    throw new IllegalArgumentException("Tried to proxy a non-whitelisted domain.");
  }

  @Override
  public void connectFailed(URI uri, SocketAddress address, IOException failure) {
    Log.w(TAG, failure);
  }

  private void initializeContentProxy() {
    CONTENT = new ArrayList<Proxy>(1) {{
      add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(BuildConfig.CONTENT_PROXY_HOST,
                                                           BuildConfig.CONTENT_PROXY_PORT)));
    }};
  }

  private List<Proxy> getOrCreateContentProxy() {
    if (CONTENT == null) {
      synchronized (this) {
        while (CONTENT == null) Util.wait(this, 0);
      }
    }

    return CONTENT;
  }

}
