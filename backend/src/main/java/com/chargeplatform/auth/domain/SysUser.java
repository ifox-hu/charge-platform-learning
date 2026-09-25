package com.chargeplatform.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@TableName("sys_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SysUser {
    @TableId(type = IdType.AUTO) private Long id;
    private String username;
    private String password;
    private String displayName;
    private String role;
    private boolean enabled;
    public SysUser(String username,String password,String displayName,String role){this.username=username;this.password=password;this.displayName=displayName;this.role=role;this.enabled=true;}
}
