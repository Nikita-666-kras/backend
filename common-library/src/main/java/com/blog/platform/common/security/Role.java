package com.blog.platform.common.security;

public enum Role {
    ADMIN,
    EDITOR,
    MANAGER;

    public String authority() {
        return "ROLE_" + name();
    }
}
