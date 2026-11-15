package mx.codi.core.domain;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
