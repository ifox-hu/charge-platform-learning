package com.chargeplatform.station.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStationRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 200) String address,
    @Size(max = 255) String description,
    @NotBlank @Pattern(regexp = "OPERATING|CLOSED") String status
) {
}
