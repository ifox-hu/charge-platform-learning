package com.chargeplatform.station.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@TableName("station")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String status;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coordinateType;

    public Station(String name, String address, String description) {
        this(name, address, description, null, null, null);
    }

    public Station(String name, String address, String description, BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = "OPERATING";
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinateType = coordinateType;
    }

    public void update(String name, String address, String description, String status) {
        update(name, address, description, status, latitude, longitude, coordinateType);
    }

    public void update(String name, String address, String description, String status, BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinateType = coordinateType;
    }

    public void updateCoordinates(BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinateType = coordinateType;
    }
}
