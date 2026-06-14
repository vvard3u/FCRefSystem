package ru.fcref.system.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.fcref.system.domain.Role;
import ru.fcref.system.domain.UserAccount;

@Service
public class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public JdbcUserDirectory(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserAccount> listUsers() {
        return jdbcTemplate.query(
                        "select id, username, display_name from app_users where enabled = true",
                        this::mapUser
                ).stream()
                .sorted(Comparator.comparing(UserAccount::getDisplayName))
                .toList();
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return findUser("select id, username, display_name from app_users where id = ? and enabled = true", userId);
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return findUser(
                "select id, username, display_name from app_users where username = ? and enabled = true",
                username
        );
    }

    @Override
    public UserAccount createUser(
            String userId,
            String username,
            String rawPassword,
            String displayName,
            Set<Role> roles
    ) {
        jdbcTemplate.update(
                "insert into app_users (id, username, password, display_name, enabled) values (?, ?, ?, ?, true)",
                userId,
                username,
                passwordEncoder.encode(rawPassword),
                displayName
        );
        roles.forEach(role -> jdbcTemplate.update(
                "insert into app_user_roles (user_id, role) values (?, ?)",
                userId,
                role.name()
        ));
        return requireById(userId);
    }

    @Override
    public UserAccount assignRole(String userId, Role role) {
        UserAccount user = requireById(userId);
        if (!user.hasRole(role)) {
            jdbcTemplate.update(
                    "insert into app_user_roles (user_id, role) values (?, ?)",
                    userId,
                    role.name()
            );
        }
        return requireById(userId);
    }

    @Override
    public UserAccount revokeRole(String userId, Role role) {
        requireById(userId);
        jdbcTemplate.update("delete from app_user_roles where user_id = ? and role = ?", userId, role.name());
        return requireById(userId);
    }

    private Optional<UserAccount> findUser(String sql, String value) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapUser, value));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private UserAccount requireById(String userId) {
        return findById(userId)
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "User not found"));
    }

    private UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        String userId = resultSet.getString("id");
        List<Role> roles = jdbcTemplate.queryForList(
                        "select role from app_user_roles where user_id = ? order by role",
                        String.class,
                        userId
                ).stream()
                .map(Role::valueOf)
                .toList();
        return new UserAccount(
                userId,
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles)
        );
    }
}
