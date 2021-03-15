package io.github.fvarrui.javapackager.utils;

/**
 * Signer helper exception 
 */
@SuppressWarnings("serial")
public class SignerException extends Exception {

    public SignerException(String message) {
        super(message);
    }

    public SignerException(String message, Throwable cause) {
        super(message, cause);
    }
    
}