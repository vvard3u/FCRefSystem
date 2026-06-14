package ru.fcref.system.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.fcref.system.domain.UserAccount;

@Service
public class CurrentUserService {

    private final UserDirectory userDirectory;

    public CurrentUserService(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    public UserAccount requireCurrent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessRuleException("ACCESS_DENIED", "Authentication is required");
        }
        return userDirectory.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "User not found"));
    }
}
