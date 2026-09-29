package com.chargeplatform.auth.security;

import com.chargeplatform.auth.domain.SysUser;
import com.chargeplatform.auth.mapper.SysUserMapper;
import com.chargeplatform.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private SysUserMapper users;

    private SysUser admin;
    private SysUser operator;

    @BeforeEach
    void setUpUsers() {
        admin = new SysUser("admin", passwordEncoder.encode("test-admin-password"), "系统管理员", "ADMIN");
        operator = new SysUser("operator", passwordEncoder.encode("test-operator-password"), "运营人员", "OPERATOR");
        when(users.selectByUsername("admin")).thenReturn(admin);
        when(users.selectByUsername("operator")).thenReturn(operator);
    }

    @Test
    void loginReturnsJwtForValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginRejectsWrongPasswordWithCommonErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void protectedBusinessGetRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/stations"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void operatorCannotCallAdminWriteEndpoint() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(post("/api/stations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"测试站\",\"address\":\"测试地址\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("当前账号没有操作权限"));
    }

    @Test
    void operatorCannotUpdateStation() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(put("/api/stations/1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"禁止修改\",\"address\":\"测试地址\",\"status\":\"OPERATING\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void operatorCannotDeleteDevice() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(delete("/api/chargers/1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void operatorCannotArchiveOrders() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(delete("/api/orders/completed-test")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1]}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void operatorCanReadOwnProfileWithJwt() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.username").value("operator"))
            .andExpect(jsonPath("$.data.role").value("OPERATOR"));
    }

    @Test
    void operatorCannotReadActuatorMetrics() throws Exception {
        String token = jwtService.create("operator", "OPERATOR");

        mockMvc.perform(get("/actuator/metrics")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }
}
