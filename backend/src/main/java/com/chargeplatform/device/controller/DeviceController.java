package com.chargeplatform.device.controller;

import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.device.domain.Charger;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.dto.DeviceRequests;
import com.chargeplatform.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping("/chargers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Charger> createCharger(@Valid @RequestBody DeviceRequests.CreateCharger request) {
        return ApiResponse.created(service.createCharger(request));
    }

    @GetMapping("/chargers")
    public ApiResponse<List<Charger>> chargers(@RequestParam(required = false) Long stationId) {
        return ApiResponse.success(service.chargers(stationId));
    }

    @PutMapping("/chargers/{id}/status")
    public ApiResponse<Charger> changeStatus(@PathVariable Long id, @Valid @RequestBody DeviceRequests.ChangeChargerStatus request) {
        return ApiResponse.success(service.changeStatus(id, request.status()));
    }

    @PutMapping("/chargers/{id}")
    public ApiResponse<Charger> updateCharger(@PathVariable Long id, @Valid @RequestBody DeviceRequests.UpdateCharger request) {
        return ApiResponse.success(service.updateCharger(id, request));
    }

    @PostMapping("/connectors")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Connector> createConnector(@Valid @RequestBody DeviceRequests.CreateConnector request) {
        return ApiResponse.created(service.createConnector(request));
    }

    @GetMapping("/connectors")
    public ApiResponse<List<Connector>> connectors(@RequestParam(required = false) Long chargerId) {
        return ApiResponse.success(service.connectors(chargerId));
    }

    @PutMapping("/connectors/{id}")
    public ApiResponse<Connector> updateConnector(@PathVariable Long id, @Valid @RequestBody DeviceRequests.UpdateConnector request) {
        return ApiResponse.success(service.updateConnector(id, request));
    }

    @DeleteMapping("/chargers/{id}")
    public ApiResponse<Void> deleteCharger(@PathVariable Long id) {
        service.deleteCharger(id);
        return ApiResponse.message("充电桩删除成功");
    }

    @DeleteMapping("/connectors/{id}")
    public ApiResponse<Void> deleteConnector(@PathVariable Long id) {
        service.deleteConnector(id);
        return ApiResponse.message("充电枪删除成功");
    }
}
