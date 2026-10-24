package com.algomentor.exception;

public class CodeforcesServiceException extends RuntimeException {

    public CodeforcesServiceException(String message) {
        super(message);
    }

    public CodeforcesServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
