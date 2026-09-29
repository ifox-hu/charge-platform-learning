package com.chargeplatform.station.service;

import com.chargeplatform.station.domain.Station;

import java.util.List;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface StationService {

    Station create(String name, String address, String description);

    Station create(String name, String address, String description, BigDecimal latitude, BigDecimal longitude, String coordinateType);

    List<Station> findAll();
    Page<Station> findPage(String name, int page, int size);

    Station findById(Long id);

    Station update(Long id, String name, String address, String description, String status);

    Station update(Long id, String name, String address, String description, String status, BigDecimal latitude, BigDecimal longitude, String coordinateType);

    Station updateCoordinates(Long id, BigDecimal latitude, BigDecimal longitude, String coordinateType);

    void delete(Long id);
}
