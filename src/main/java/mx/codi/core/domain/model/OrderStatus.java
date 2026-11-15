package mx.codi.core.domain.model;

import java.util.Locale;

public enum OrderStatus {
    SELECTING_EVENT("seleccionando_evento"),
    PENDING_PAYMENT("pendiente_de_pago"),
    PAID("pagada"),
    TICKET_SENT("boleto_enviado"),
    EXPIRED("expirada"),
    CANCELLED("cancelada");

    private final String wireValue;

    OrderStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static OrderStatus fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("order status is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (OrderStatus status : values()) {
            if (status.wireValue.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown order status");
    }
}
