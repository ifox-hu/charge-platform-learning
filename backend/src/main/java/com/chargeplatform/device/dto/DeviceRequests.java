package com.chargeplatform.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class DeviceRequests {
    private DeviceRequests() {
    }

    public record CreateCharger(@NotNull Long stationId, @NotBlank String code, @NotBlank String name) {
    }

    public record CreateConnector(@NotNull Long chargerId, @NotBlank String code, @NotBlank String name,
                                  @NotNull @Min(1) Integer ratedPower) {
    }

    public record ChangeChargerStatus(@NotBlank @Pattern(regexp = "ONLINE|OFFLINE|FAULT") String status) {
    }

    public record UpdateCharger(@NotBlank String code, @NotBlank String name,
                                @NotBlank @Pattern(regexp = "ONLINE|OFFLINE|FAULT") String status) {
    }

    public record UpdateConnector(@NotBlank String code, @NotBlank String name,
                                  @NotNull @Min(1) Integer ratedPower) {
    }
}
