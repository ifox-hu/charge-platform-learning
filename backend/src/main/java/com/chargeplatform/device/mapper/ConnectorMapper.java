package com.chargeplatform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.device.domain.Connector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConnectorMapper extends BaseMapper<Connector> {
    @Select("SELECT * FROM connector WHERE charger_id = #{chargerId} ORDER BY id ASC")
    List<Connector> selectByChargerId(@Param("chargerId") Long chargerId);

    @Select("SELECT COUNT(*) > 0 FROM connector WHERE charger_id = #{chargerId}")
    boolean existsByChargerId(@Param("chargerId") Long chargerId);

    @Select("SELECT * FROM connector WHERE id = #{id} FOR UPDATE")
    Connector selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM connector WHERE charger_id = #{chargerId} AND status = #{status}")
    boolean existsByChargerIdAndStatus(@Param("chargerId") Long chargerId, @Param("status") String status);
}
