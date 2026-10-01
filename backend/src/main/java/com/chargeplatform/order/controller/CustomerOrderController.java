package com.chargeplatform.order.controller;

import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.order.domain.ChargeOrder;
import com.chargeplatform.order.dto.OrderRequests;
import com.chargeplatform.order.service.ChargeOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/orders")
@PreAuthorize("hasRole('USER')")
public class CustomerOrderController {
    private final ChargeOrderService service;
    public CustomerOrderController(ChargeOrderService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<ChargeOrder>> list(Authentication auth) {
        return ApiResponse.success(service.customerOrders(auth.getName()));
    }
    @GetMapping("/{id}")
    public ApiResponse<ChargeOrder> get(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(service.customerOrder(id, auth.getName()));
    }
    @PostMapping("/start")
    public ApiResponse<ChargeOrder> start(@Valid @RequestBody OrderRequests.Start request, Authentication auth) {
        // All customer sessions in this learning project use simulated devices and payments.
        return ApiResponse.created(service.start(new OrderRequests.Start(request.stationId(), request.connectorId(), request.plateNumber(), true), auth.getName()));
    }
    @PostMapping("/{id}/stop")
    public ApiResponse<ChargeOrder> stop(@PathVariable Long id, Authentication auth) {
        service.customerOrder(id, auth.getName());
        return ApiResponse.success(service.stop(id, null));
    }
    @GetMapping("/{id}/live")
    public ApiResponse<ChargeOrderService.OrderLiveStatus> live(@PathVariable Long id, Authentication auth) {
        service.customerOrder(id, auth.getName());
        return ApiResponse.success(service.live(id));
    }
    public enum Method { WECHAT, ALIPAY }
    public record Payment(@NotNull Method method) { }
    @PostMapping("/{id}/mock-payment")
    public ApiResponse<ChargeOrder> pay(@PathVariable Long id, @Valid @RequestBody Payment payment, Authentication auth) {
        return ApiResponse.success(service.mockPay(id, auth.getName(), payment.method().name()));
    }
}
