package com.blog.platform.common.security;

public enum Role {
    ADMIN,
    EDITOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
