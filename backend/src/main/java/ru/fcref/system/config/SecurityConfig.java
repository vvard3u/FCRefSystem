package ru.fcref.system.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/assets/**", "/openapi.yaml").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/invitations/activate").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(JdbcTemplate jdbcTemplate) {
        return username -> {
            List<SecurityUserRow> rows = jdbcTemplate.query(
                    "select username, password, enabled from app_users where username = ?",
                    (resultSet, rowNumber) -> new SecurityUserRow(
                            resultSet.getString("username"),
                            resultSet.getString("password"),
                            resultSet.getBoolean("enabled")
                    ),
                    username
            );
            if (rows.isEmpty()) {
                throw new UsernameNotFoundException(username);
            }
            SecurityUserRow user = rows.get(0);
            List<SimpleGrantedAuthority> authorities = jdbcTemplate.queryForList(
                            """
                            select role
                            from app_user_roles roles
                            join app_users users on users.id = roles.user_id
                            where users.username = ?
                            """,
                            String.class,
                            username
                    ).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.username())
                    .password(user.password())
                    .authorities(authorities)
                    .disabled(!user.enabled())
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private record SecurityUserRow(String username, String password, boolean enabled) {
    }
}
