package com.blog.platform.integrations.config;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RussianTrustedCaTest {

    @Test
    void sslContext_loadsMincifryCerts() {
        SSLContext ctx = RussianTrustedCa.sslContext();
        assertNotNull(ctx);
        assertNotNull(ctx.getSocketFactory());
    }
}
