package ru.fcref.system.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import ru.fcref.system.domain.Role;
import ru.fcref.system.domain.UserAccount;

public interface UserDirectory {

    List<UserAccount> listUsers();

    Optional<UserAccount> findById(String userId);

    Optional<UserAccount> findByUsername(String username);

    UserAccount createUser(
            String userId,
            String username,
            String rawPassword,
            String displayName,
            Set<Role> roles
    );

    UserAccount assignRole(String userId, Role role);

    UserAccount revokeRole(String userId, Role role);
}
