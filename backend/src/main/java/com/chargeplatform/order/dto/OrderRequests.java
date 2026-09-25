package com.chargeplatform.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class OrderRequests {
    private OrderRequests() {
    }

    public record Start(@NotNull Long stationId, @NotNull Long connectorId, @NotBlank String plateNumber) {
    }

    public record Stop(@NotNull @DecimalMin(value = "0.001") BigDecimal energyKwh) {
    }
}
