package com.chargeplatform.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chargeplatform.audit.domain.AuditLog;
import com.chargeplatform.audit.mapper.AuditLogMapper;
import com.chargeplatform.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditLogService {
    private final AuditLogMapper logs;

    public AuditLogService(AuditLogMapper logs) {
        this.logs = logs;
    }

    public Page<AuditLog> page(String username, int page, int size) {
        String keyword = username == null ? "" : username.trim();
        Page<AuditLog> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return logs.selectPage(result, new LambdaQueryWrapper<AuditLog>()
                .like(!keyword.isEmpty(), AuditLog::getUsername, keyword)
                .orderByDesc(AuditLog::getId));
    }

    public int cleanup(int days) {
        if (days != 7 && days != 30) throw new BusinessException(400, "日志清理周期只能是7天或30天");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return logs.delete(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
    }
}
