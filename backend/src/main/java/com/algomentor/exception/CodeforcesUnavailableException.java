package com.algomentor.exception;

public class CodeforcesUnavailableException extends RuntimeException {

    public CodeforcesUnavailableException(String message) {
        super(message);
    }

    public CodeforcesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
