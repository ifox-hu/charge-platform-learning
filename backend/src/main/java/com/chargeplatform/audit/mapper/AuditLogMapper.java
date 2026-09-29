package com.chargeplatform.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.audit.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
