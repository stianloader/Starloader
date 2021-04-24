package de.geolykt.starloader;

/**
 * Exception that will likely not be thrown unless the universe got destroyed.
 * Handle this exception with care, as this state has been forseen, but also not.
 */
public class UnlikelyEventException extends RuntimeException {

    private static final long serialVersionUID = -2888062699847356220L;

    public UnlikelyEventException() {
        super("Did you just destroy the logic of the universe?");
    }
}
