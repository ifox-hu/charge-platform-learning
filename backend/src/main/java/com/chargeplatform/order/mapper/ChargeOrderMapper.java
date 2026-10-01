package com.chargeplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.order.domain.ChargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {
    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE connector_id = #{connectorId} AND status = #{status} AND archived = 0")
    boolean existsByConnectorIdAndStatus(@Param("connectorId") Long connectorId, @Param("status") String status);

    @Update("UPDATE charge_order SET energy_kwh = #{energyKwh} WHERE connector_id = #{connectorId} AND status = 'CHARGING' AND archived = 0")
    int updateChargingEnergy(@Param("connectorId") Long connectorId, @Param("energyKwh") BigDecimal energyKwh);

    @Select("SELECT * FROM charge_order WHERE id = #{id} AND status = #{status} AND archived = 0")
    ChargeOrder selectByIdAndStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT * FROM charge_order WHERE status = 'CHARGING' AND archived = 0 ORDER BY id ASC")
    List<ChargeOrder> selectChargingOrders();

    @Select("SELECT * FROM charge_order WHERE id = #{id} AND archived = 0 FOR UPDATE")
    ChargeOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE station_id = #{stationId} AND archived = 0")
    boolean existsByStationId(@Param("stationId") Long stationId);

    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE connector_id = #{connectorId} AND archived = 0")
    boolean existsByConnectorId(@Param("connectorId") Long connectorId);

    @Select("SELECT COUNT(*) FROM charge_order WHERE status = #{status} AND archived = 0")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) > 0 FROM charge_order WHERE owner_username = #{username}")
    boolean existsByOwnerUsername(@Param("username") String username);

    @Select("SELECT * FROM charge_order WHERE owner_username = #{username} ORDER BY id DESC")
    List<ChargeOrder> selectByOwnerUsername(@Param("username") String username);

    @Update("UPDATE charge_order SET owner_username = NULL WHERE owner_username = #{username}")
    int clearOwnerUsername(@Param("username") String username);

}
