package com.chargeplatform.station.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public Station(String name, String address, String description) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = "OPERATING";
    }

    public Station(Long id, String name, String address, String description) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = "OPERATING";
    }

    public void update(String name, String address, String description, String status) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = status;
    }
}
