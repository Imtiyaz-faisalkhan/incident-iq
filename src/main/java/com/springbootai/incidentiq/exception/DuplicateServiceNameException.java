package com.springbootai.incidentiq.exception;

public class DuplicateServiceNameException extends RuntimeException {

    public DuplicateServiceNameException(String name) {
        super(message(name));
    }

    public DuplicateServiceNameException(String name, Throwable cause) {
        super(message(name), cause);
    }

    private static String message(String name) {
        return "A service named '" + name + "' already exists";
    }
}
