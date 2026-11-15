package mx.codi.core.domain.port.in;

import mx.codi.core.domain.model.Order;

public record ChargedOrder(Order order, String qrString, String paymentLinkUrl) {
}
