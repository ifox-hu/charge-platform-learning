package com.chargeplatform.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class OrderRequests {
    private OrderRequests() {
    }

    public record Start(@NotNull Long stationId, @NotNull Long connectorId, @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼港澳台][A-Z][0-9]{5,6}$", message = "车牌号格式应为地区简称+大写字母+5或6位数字") String plateNumber, Boolean testOrder) {
    }

    public record Stop(@DecimalMin(value = "0.001") BigDecimal energyKwh) {
    }

    public record Cleanup(List<Long> ids) {
    }
}
