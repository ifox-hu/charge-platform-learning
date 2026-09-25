package com.chargeplatform.order.event;

import java.math.BigDecimal;

public record OrderCompletedEvent(Long orderId, Long stationId, BigDecimal totalAmount) {
}
