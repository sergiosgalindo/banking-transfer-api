package mx.codi.core.domain;

public class SoldOutException extends RuntimeException {

    public SoldOutException() {
        super("not enough seats");
    }
}
