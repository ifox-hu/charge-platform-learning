package com.chargeplatform.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@TableName("connector")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Connector {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chargerId;
    private String code;
    private String name;
    private Integer ratedPower;
    private String status;

    public Connector(Long chargerId, String code, String name, Integer ratedPower) {
        this.chargerId = chargerId; this.code = code; this.name = name; this.ratedPower = ratedPower; this.status = "IDLE";
    }
    public Connector(Long id, Long chargerId, String code, String name, Integer ratedPower) {
        this.id = id; this.chargerId = chargerId; this.code = code; this.name = name; this.ratedPower = ratedPower; this.status = "IDLE";
    }
    public void changeStatus(String status) { this.status = status; }
}
