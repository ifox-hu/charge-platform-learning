package com.chargeplatform.auth.service;
import com.chargeplatform.auth.domain.SysUser;
import com.chargeplatform.auth.dto.AuthDtos;
import com.chargeplatform.auth.mapper.SysUserMapper;
import com.chargeplatform.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
    private final SysUserMapper users;private final PasswordEncoder encoder;private final JwtService jwt;
    public AuthService(SysUserMapper users,PasswordEncoder encoder,JwtService jwt){this.users=users;this.encoder=encoder;this.jwt=jwt;}
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request){
        SysUser user=users.selectByUsername(request.username());
        if(user == null || !user.isEnabled()) throw new BusinessException(401,"用户名或密码错误");
        if(!encoder.matches(request.password(),user.getPassword())) throw new BusinessException(401,"用户名或密码错误");
        return new AuthDtos.LoginResponse(jwt.create(user.getUsername(),user.getRole()),"Bearer",jwt.getExpirationSeconds(),user.getUsername(),user.getDisplayName(),user.getRole());
    }
    public AuthDtos.UserProfile profile(String username){SysUser user=users.selectByUsername(username);if(user==null) throw new BusinessException(404,"用户不存在");return new AuthDtos.UserProfile(user.getUsername(),user.getDisplayName(),user.getRole());}
}
