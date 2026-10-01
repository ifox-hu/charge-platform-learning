package com.chargeplatform.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@TableName("charge_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeOrder {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo;
    private Long stationId; private Long connectorId; private String plateNumber; private String status;
    private Boolean testOrder;
    private Boolean archived;
    private LocalDateTime archivedAt;
    private LocalDateTime startTime; private LocalDateTime endTime;
    private BigDecimal energyKwh;
    private BigDecimal electricityFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private String ownerUsername;
    private String paymentStatus = "NOT_REQUIRED";
    private String paymentMethod;
    private String paymentNo;
    private LocalDateTime paidAt;
    public void assignCustomer(String username) { this.ownerUsername = username; this.paymentStatus = "UNPAID"; }
    public void payMock(String method) {
        this.paymentStatus = "PAID";
        this.paymentMethod = method;
        this.paymentNo = "MOCK-" + java.util.UUID.randomUUID();
        this.paidAt = LocalDateTime.now();
    }
    public ChargeOrder(String orderNo, Long stationId, Long connectorId, String plateNumber) {
        this(orderNo, stationId, connectorId, plateNumber, false);
    }
    public ChargeOrder(String orderNo, Long stationId, Long connectorId, String plateNumber, boolean testOrder) {
        this.orderNo=orderNo;this.stationId=stationId;this.connectorId=connectorId;this.plateNumber=plateNumber;this.status="CHARGING";this.testOrder=testOrder;this.archived=false;this.startTime=LocalDateTime.now();this.energyKwh=BigDecimal.ZERO;this.electricityFee=BigDecimal.ZERO;this.serviceFee=BigDecimal.ZERO;this.totalAmount=BigDecimal.ZERO;
    }
    public void complete(BigDecimal energy, BigDecimal electricFee, BigDecimal serviceFee, LocalDateTime endTime) { this.energyKwh=energy;this.electricityFee=electricFee;this.serviceFee=serviceFee;this.totalAmount=electricFee.add(serviceFee);this.status="COMPLETED";this.endTime=endTime; }
    public void archive() { this.archived=true; this.archivedAt=LocalDateTime.now(); }
}
