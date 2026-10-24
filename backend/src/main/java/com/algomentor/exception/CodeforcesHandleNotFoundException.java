package com.algomentor.exception;

public class CodeforcesHandleNotFoundException extends RuntimeException {

    public CodeforcesHandleNotFoundException(String handle) {
        super("Codeforces handle '" + handle + "' does not exist");
    }
}
