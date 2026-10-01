package com.chargeplatform.auth.service;
import com.chargeplatform.auth.domain.SysUser;
import com.chargeplatform.auth.dto.AuthDtos;
import com.chargeplatform.auth.mapper.SysUserMapper;
import com.chargeplatform.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import java.util.List;
import java.util.stream.Collectors;
@Service
public class AuthService {
    private final SysUserMapper users;private final PasswordEncoder encoder;private final JwtService jwt; private final ChargeOrderMapper orders;
    public AuthService(SysUserMapper users,PasswordEncoder encoder,JwtService jwt, ChargeOrderMapper orders){this.users=users;this.encoder=encoder;this.jwt=jwt;this.orders=orders;}
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request){
        SysUser user=users.selectByUsername(request.username());
        if(user == null || !user.isEnabled()) throw new BusinessException(401,"用户名或密码错误");
        if(!encoder.matches(request.password(),user.getPassword())) throw new BusinessException(401,"用户名或密码错误");
        return new AuthDtos.LoginResponse(jwt.create(user.getUsername(),user.getRole()),"Bearer",jwt.getExpirationSeconds(),user.getUsername(),user.getDisplayName(),user.getRole());
    }
    public AuthDtos.UserProfile profile(String username){SysUser user=users.selectByUsername(username);if(user==null) throw new BusinessException(404,"用户不存在");return new AuthDtos.UserProfile(user.getUsername(),user.getDisplayName(),user.getRole());}
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        if (users.selectByUsername(request.username()) != null) throw new BusinessException(409, "用户名已存在");
        users.insert(new SysUser(request.username(), encoder.encode(request.password()), request.displayName(), "USER"));
        return login(new AuthDtos.LoginRequest(request.username(), request.password()));
    }

    public AuthDtos.UserPage pageUsers(int page, int size, String keyword, String role) {
        Page<SysUser> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        String query = keyword == null ? "" : keyword.trim();
        users.selectPage(result, new LambdaQueryWrapper<SysUser>()
                .and(!query.isEmpty(), wrapper -> wrapper.like(SysUser::getUsername, query).or().like(SysUser::getDisplayName, query))
                .eq(role != null && !role.isBlank(), SysUser::getRole, role.trim())
                .orderByDesc(SysUser::getId));
        List<AuthDtos.UserAdminView> rows = result.getRecords().stream()
                .map(user -> new AuthDtos.UserAdminView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isEnabled()))
                .collect(Collectors.toList());
        return new AuthDtos.UserPage(rows, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public void setEnabled(Long id, boolean enabled, String operator) {
        SysUser user = requireUser(id);
        if (user.getUsername().equals(operator)) throw new BusinessException(409, "不能修改当前登录账号状态");
        if ("ADMIN".equals(user.getRole()) && !enabled) throw new BusinessException(403, "不能禁用管理员账号");
        users.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, id).set(SysUser::isEnabled, enabled));
    }

    public void resetPassword(Long id, AuthDtos.ResetPasswordRequest request) {
        requireUser(id);
        users.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encoder.encode(request.password())));
    }

    public void deleteUser(Long id, String operator) {
        SysUser user = requireUser(id);
        if (user.getUsername().equals(operator)) throw new BusinessException(409, "不能删除当前登录账号");
        if (!"USER".equals(user.getRole())) throw new BusinessException(403, "只能删除普通用户账号");
        if (orders.existsByOwnerUsername(user.getUsername())) throw new BusinessException(409, "该用户已有订单，请改用禁用账号");
        users.deleteById(id);
    }

    private SysUser requireUser(Long id) {
        SysUser user = users.selectById(id);
        if (user == null) throw new BusinessException(404, "用户不存在");
        return user;
    }
}
