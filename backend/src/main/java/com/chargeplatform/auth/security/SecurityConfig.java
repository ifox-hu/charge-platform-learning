package com.chargeplatform.auth.security;

import com.chargeplatform.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt,ObjectMapper objectMapper)throws Exception{
        http.csrf(csrf->csrf.disable())
            .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers->headers.frameOptions(frame->frame.sameOrigin()))
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((request,response,exception)->writeError(response,objectMapper,401,"请先登录"))
                .accessDeniedHandler((request,response,exception)->writeError(response,objectMapper,403,"当前账号没有操作权限")))
            .authorizeHttpRequests(auth->auth
                .requestMatchers("/api/health/**","/api/auth/login","/api/auth/register","/api/hello/**","/actuator/health","/actuator/info","/h2-console/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers("/api/audit-logs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/auth/users").hasAnyRole("ADMIN","OPERATOR")
                .requestMatchers("/api/auth/users/**").hasRole("ADMIN")
                .requestMatchers("/api/customer/**").hasRole("USER")
                .requestMatchers(HttpMethod.GET,"/api/auth/me","/api/stations/**","/api/chargers/**","/api/connectors/**","/api/price-periods/**","/api/simulator/status").hasAnyRole("ADMIN","OPERATOR","USER")
                .requestMatchers(HttpMethod.GET,"/api/**").hasAnyRole("ADMIN","OPERATOR")
                .requestMatchers(HttpMethod.POST,"/api/orders/**").hasAnyRole("ADMIN","OPERATOR")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response,ObjectMapper objectMapper,int status,String message)throws java.io.IOException{
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),ApiResponse.error(status,message));
    }
}
