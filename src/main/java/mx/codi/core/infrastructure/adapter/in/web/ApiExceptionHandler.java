package mx.codi.core.infrastructure.adapter.in.web;

import java.util.Map;
import mx.codi.core.domain.InvalidOrderTransitionException;
import mx.codi.core.domain.NotFoundException;
import mx.codi.core.domain.PaymentGatewayException;
import mx.codi.core.domain.SoldOutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> missing(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SoldOutException.class)
    public ResponseEntity<Map<String, String>> soldOut(SoldOutException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<Map<String, String>> transition(InvalidOrderTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, String>> gateway(PaymentGatewayException exception) {
        String causeMsg = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "payment gateway failed",
                "detail", causeMsg == null ? "unknown" : causeMsg));
    }
}
