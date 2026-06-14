package ru.fcref.system.domain;

import java.util.EnumSet;
import java.util.Set;

public class UserAccount {

    private final String id;
    private final String displayName;
    private final Set<Role> roles;

    public UserAccount(String id, String displayName, Set<Role> roles) {
        this.id = id;
        this.displayName = displayName;
        this.roles = EnumSet.copyOf(roles);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public void assignRole(Role role) {
        roles.add(role);
    }

    public void revokeRole(Role role) {
        roles.remove(role);
    }
}
