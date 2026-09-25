package com.chargeplatform.station.service;

import com.chargeplatform.station.domain.Station;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface StationService {

    Station create(String name, String address, String description);

    List<Station> findAll();
    Page<Station> findPage(String name, int page, int size);

    Station findById(Long id);

    Station update(Long id, String name, String address, String description, String status);

    void delete(Long id);
}
