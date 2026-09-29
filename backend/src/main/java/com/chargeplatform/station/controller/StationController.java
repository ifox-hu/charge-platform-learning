package com.chargeplatform.station.controller;

import com.chargeplatform.station.domain.Station;
import com.chargeplatform.station.dto.CreateStationRequest;
import com.chargeplatform.station.dto.UpdateStationRequest;
import com.chargeplatform.station.service.StationService;
import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.common.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Station> create(@Valid @RequestBody CreateStationRequest request) {
        return ApiResponse.created(stationService.create(request.getName(), request.getAddress(), request.getDescription(), request.getLatitude(), request.getLongitude(), request.getCoordinateType()));
    }

    @GetMapping
    public ApiResponse<List<Station>> findAll() {
        return ApiResponse.success(stationService.findAll());
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<Station>> findPage(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(PageResponse.from(stationService.findPage(name, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Station> findById(@PathVariable Long id) {
        return ApiResponse.success(stationService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Station> update(@PathVariable Long id, @Valid @RequestBody UpdateStationRequest request) {
        return ApiResponse.success(stationService.update(id, request.name(), request.address(), request.description(), request.status(), request.latitude(), request.longitude(), request.coordinateType()));
    }

    @PutMapping("/{id}/coordinates")
    public ApiResponse<Station> updateCoordinates(@PathVariable Long id, @Valid @RequestBody CoordinateRequest request) {
        return ApiResponse.success(stationService.updateCoordinates(id, request.latitude(), request.longitude(), request.coordinateType()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        stationService.delete(id);
        return ApiResponse.message("充电站删除成功");
    }

    public record CoordinateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 16) String coordinateType) { }
}
