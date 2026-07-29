package com.blog.platform.common.security;

public enum Role {
    ADMIN,
    EDITOR,
    MANAGER,
    PURCHASER;

    public String authority() {
        return "ROLE_" + name();
    }
}
