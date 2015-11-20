package org.thoughtcrime.securesms.database;

public class DraftFactory {

    private static DraftFactory instance;
    private static Object lock = new Object();

    public static DraftFactory getInstance() {
        synchronized (lock) {
            if (instance == null) {
                instance = new DraftFactory();
            }
            return instance;
        }
    }

    public Draft getDraft(String type, String value) {
        switch (type) {
            case Draft.TEXT:  return new TextDraft(value);
            case Draft.IMAGE: return new ImageDraft(value);
            case Draft.VIDEO: return new VideoDraft(value);
            case Draft.AUDIO: return new AudioDraft(value);
            default:    return null;
        }
    }

}
