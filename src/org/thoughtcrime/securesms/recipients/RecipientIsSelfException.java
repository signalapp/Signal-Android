package org.thoughtcrime.securesms.recipients;

/**
 * Created by e on 2/25/14.
 */
public class RecipientIsSelfException extends Exception {
    public RecipientIsSelfException() {
        super();
    }

    public RecipientIsSelfException(String message) {
        super(message);
    }

    public RecipientIsSelfException(String message, Throwable nested) {
        super(message, nested);
    }

    public RecipientIsSelfException(Throwable nested) {
        super(nested);
    }
}
