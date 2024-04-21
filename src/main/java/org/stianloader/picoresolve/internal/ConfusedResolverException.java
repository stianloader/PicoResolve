package org.stianloader.picoresolve.internal;

/**
 * Exception that is thrown when something just doesn't add up.
 */
public class ConfusedResolverException extends RuntimeException {

    private static final long serialVersionUID = -9157926033984425204L;

    public ConfusedResolverException(String message) {
        super(message);
    }
}
