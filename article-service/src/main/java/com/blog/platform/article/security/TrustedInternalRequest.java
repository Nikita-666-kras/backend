package com.blog.platform.article.security;

import com.blog.platform.common.security.InternalAuthSupport;
import jakarta.servlet.http.HttpServletRequest;

public final class TrustedInternalRequest {

    private TrustedInternalRequest() {
    }

    public static boolean isTrusted(HttpServletRequest request) {
        Object value = request.getAttribute(InternalAuthSupport.TRUSTED_INTERNAL_ATTRIBUTE);
        return Boolean.TRUE.equals(value);
    }
}
