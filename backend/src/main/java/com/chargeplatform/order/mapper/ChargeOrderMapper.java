package com.chargeplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.order.domain.ChargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {
    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE connector_id = #{connectorId} AND status = #{status}")
    boolean existsByConnectorIdAndStatus(@Param("connectorId") Long connectorId, @Param("status") String status);

    @Update("UPDATE charge_order SET energy_kwh = #{energyKwh} WHERE connector_id = #{connectorId} AND status = 'CHARGING'")
    int updateChargingEnergy(@Param("connectorId") Long connectorId, @Param("energyKwh") BigDecimal energyKwh);

    @Select("SELECT * FROM charge_order WHERE id = #{id} AND status = #{status}")
    ChargeOrder selectByIdAndStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT * FROM charge_order WHERE id = #{id} FOR UPDATE")
    ChargeOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE station_id = #{stationId}")
    boolean existsByStationId(@Param("stationId") Long stationId);

    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE connector_id = #{connectorId}")
    boolean existsByConnectorId(@Param("connectorId") Long connectorId);

    @Select("SELECT COUNT(*) FROM charge_order WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
