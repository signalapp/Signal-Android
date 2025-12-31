package org.signal.devicetransfer;

/**
 * Allow {@link DeviceTransferClient} or {@link DeviceTransferServer} to indicate to the
 * {@link DeviceToDeviceTransferService} that an internal issue caused a shutdown and the
 * service should stop as well.
 */
interface ShutdownCallback {
  void shutdown();
}
