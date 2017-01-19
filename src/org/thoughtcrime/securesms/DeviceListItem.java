package org.thoughtcrime.securesms;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.util.Locale;

public class DeviceListItem extends LinearLayout {

  private long     deviceId;
  private TextView name;
  private TextView created;
  private TextView lastActive;

  public DeviceListItem(Context context) {
    super(context);
  }

  public DeviceListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.name       = (TextView) findViewById(R.id.name);
    this.created    = (TextView) findViewById(R.id.created);
    this.lastActive = (TextView) findViewById(R.id.active);
  }

  public void set(DeviceInfo deviceInfo, Locale locale) {
    if (TextUtils.isEmpty(deviceInfo.getName())) this.name.setText(R.string.DeviceListItem_unnamed_device);
    else                                         this.name.setText(deviceInfo.getName());

    this.created.setText(getContext().getString(R.string.DeviceListItem_linked_s,
                                                DateUtils.getDayPrecisionTimeSpanString(getContext(),
                                                                                        locale,
                                                                                        deviceInfo.getCreated())));

    this.lastActive.setText(getContext().getString(R.string.DeviceListItem_last_active_s,
                                                   DateUtils.getDayPrecisionTimeSpanString(getContext(),
                                                                                           locale,
                                                                                           deviceInfo.getLastSeen())));

    this.deviceId = deviceInfo.getId();
  }

  public long getDeviceId() {
    return deviceId;
  }

  public String getDeviceName() {
    return name.getText().toString();
  }

}
