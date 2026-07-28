package com.sebin.secondhand_market.global.config;

import com.sebin.secondhand_market.global.security.JwtAuthenticationEntryPoint;
import com.sebin.secondhand_market.global.security.JwtAuthenticationFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
      JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(form -> form.disable())
        .rememberMe(rememberMeConfigurer -> rememberMeConfigurer.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
        .authorizeHttpRequests(auth -> auth.requestMatchers(
                "/api/auth/signup", "/api/auth/login", "/api/auth/reissue"
            ).permitAll().requestMatchers(
                "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"
            ).permitAll()
            .requestMatchers("/ws-chat/**").permitAll()
            .requestMatchers("/actuator/**").denyAll()
            .anyRequest().authenticated())
        // 필터 위치 조정
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // 비밀번호 해싱 인코더 설정
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  // CORS 설정
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // 프론트 개발 서버 허용. 5173은 Vite 기본 포트 — 3000은 docker compose의 Grafana가 쓰고 있다.
    config.setAllowedOrigins(List.of(
        "http://localhost:3000",
        "http://localhost:5173"
    ));

    // PATCH가 빠져 있어 상품 상태 변경(PATCH /api/products/{id}/status)이
    // 브라우저에서 preflight 단계에 막혔다.
    config.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    ));

    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
  }


}
