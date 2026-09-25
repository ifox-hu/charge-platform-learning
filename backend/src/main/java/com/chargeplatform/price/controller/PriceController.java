package com.chargeplatform.price.controller;

import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.price.domain.PricePeriod;
import com.chargeplatform.price.service.PriceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/price-periods")
public class PriceController {
    private final PriceService service;

    public PriceController(PriceService service) {
        this.service = service;
    }

    public record Request(@NotNull Long stationId, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                          @NotNull @DecimalMin("0.0") BigDecimal electricityPrice,
                          @NotNull @DecimalMin("0.0") BigDecimal servicePrice) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PricePeriod> create(@Valid @RequestBody Request r) {
        return ApiResponse.created(service.create(r.stationId(), r.startTime(), r.endTime(), r.electricityPrice(), r.servicePrice()));
    }

    @GetMapping
    public ApiResponse<List<PricePeriod>> list(@RequestParam Long stationId) {
        return ApiResponse.success(service.list(stationId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("电价时段删除成功");
    }
}
