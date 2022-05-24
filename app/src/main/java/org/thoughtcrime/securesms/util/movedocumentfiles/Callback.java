package org.thoughtcrime.securesms.util.movedocumentfiles;

public class Callback {
    private long totalLength;
    public void setTotalLength(long totalLength) {
        this.totalLength = totalLength;
    }
    public long getTotalLength() {
        return totalLength;
    }
    public void onSuccess() { }
    public void onProgress(long bytesCopied) { }
    public void onPartialSuccess() {  }
    public void onError(ErrorType errorType) { }
    public void onFinish() { }
}
