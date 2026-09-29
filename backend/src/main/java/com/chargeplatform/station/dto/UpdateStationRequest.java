package com.chargeplatform.station.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateStationRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 200) String address,
    @Size(max = 255) String description,
    @NotBlank @Pattern(regexp = "OPERATING|CLOSED") String status,
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal latitude,
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal longitude,
    @Size(max = 16) String coordinateType
) {
}
