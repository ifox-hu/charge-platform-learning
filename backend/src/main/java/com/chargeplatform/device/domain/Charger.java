package com.chargeplatform.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@TableName("charger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Charger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private String code;
    private String name;
    private String status;

    public Charger(Long stationId, String code, String name) {
        this.stationId = stationId; this.code = code; this.name = name; this.status = "ONLINE";
    }
    public Charger(Long id, Long stationId, String code, String name) {
        this.id = id; this.stationId = stationId; this.code = code; this.name = name; this.status = "ONLINE";
    }
    public void updateStatus(String status) { this.status = status; }
}
