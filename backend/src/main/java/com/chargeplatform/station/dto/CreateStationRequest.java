package com.chargeplatform.station.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public class CreateStationRequest {

    @NotBlank(message = "站点名称不能为空")
    @Size(max = 50, message = "站点名称不能超过 50 个字符")
    private String name;

    @NotBlank(message = "站点地址不能为空")
    @Size(max = 200, message = "站点地址不能超过 200 个字符")
    private String address;

    @Size(max = 255, message = "站点描述不能超过 255 个字符")
    private String description;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @Size(max = 16)
    private String coordinateType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getCoordinateType() { return coordinateType; }
    public void setCoordinateType(String coordinateType) { this.coordinateType = coordinateType; }
}
