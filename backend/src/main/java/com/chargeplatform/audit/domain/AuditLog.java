package com.chargeplatform.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String role;
    private String method;
    private String path;
    private Integer statusCode;
    private String clientIp;
    private LocalDateTime createdAt;

    public AuditLog(String username, String role, String method, String path, int statusCode, String clientIp) {
        this.username = username;
        this.role = role;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.clientIp = clientIp;
        this.createdAt = LocalDateTime.now();
    }
}
