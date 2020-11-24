package org.thoughtcrime.securesms;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.util.DateUtils;

import java.util.Locale;

import network.loki.messenger.R;

public class DeviceListItem extends LinearLayout {

  private String   deviceId;
  private TextView name;
  private TextView shortId;

  public DeviceListItem(Context context) {
    super(context);
  }

  public DeviceListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.name = (TextView) findViewById(R.id.name);
    this.shortId = (TextView) findViewById(R.id.shortId);
  }

  public void set(Device deviceInfo, Locale locale) {
    this.deviceId = deviceInfo.getId();
    boolean hasName = !TextUtils.isEmpty(deviceInfo.getName());
    this.name.setText(hasName ? deviceInfo.getName() : deviceInfo.getShortId());
    this.shortId.setText(deviceInfo.getShortId());
    this.shortId.setVisibility(hasName ? VISIBLE : GONE);
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getDeviceName() {
    return name.getText().toString();
  }

  public boolean hasDeviceName() {
    return shortId.getVisibility() == VISIBLE;
  }

}
