package com.chargeplatform.order.controller;

import com.chargeplatform.order.domain.ChargeOrder;
import com.chargeplatform.order.dto.OrderRequests;
import com.chargeplatform.order.service.ChargeOrderService;
import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class ChargeOrderController {
    private final ChargeOrderService service;

    public ChargeOrderController(ChargeOrderService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChargeOrder> start(@Valid @RequestBody OrderRequests.Start request) {
        return ApiResponse.created(service.start(request));
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<ChargeOrder> stop(@PathVariable Long id, @Valid @RequestBody(required = false) OrderRequests.Stop request) {
        return ApiResponse.success(service.stop(id, request));
    }

    @GetMapping("/{id}/live")
    public ApiResponse<ChargeOrderService.OrderLiveStatus> live(@PathVariable Long id) {
        return ApiResponse.success(service.live(id));
    }

    @GetMapping
    public ApiResponse<List<ChargeOrder>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<ChargeOrder>> page(@RequestParam(defaultValue = "") String plateNumber, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(PageResponse.from(service.page(plateNumber, page, size)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String plateNumber) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=charge-orders.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.exportCsv(plateNumber));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChargeOrder> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @DeleteMapping("/completed-test")
    public ApiResponse<Void> clearCompletedTestOrders(@RequestBody OrderRequests.Cleanup request) {
        int count = service.clearCompletedTestOrders(request == null ? null : request.ids());
        return ApiResponse.message("已归档 " + count + " 条已完成测试订单");
    }
}
