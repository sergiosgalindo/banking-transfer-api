package mx.codi.core.infrastructure.adapter.out.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import mx.codi.core.domain.model.OrderStatus;

@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return status.wireValue();
    }

    @Override
    public OrderStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return OrderStatus.fromWireValue(value);
    }
}
