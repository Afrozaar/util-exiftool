package com.afrozaar.util.exiftool;

/**
 * @author johan
 */
public class ExiftoolException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -6102947878706572770L;

    public ExiftoolException() {
        super();
    }

    public ExiftoolException(Throwable cause) {
        super(cause);
    }

    public ExiftoolException(String message) {
        super(message);
    }

    public ExiftoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
