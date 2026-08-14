package com.blog.platform.integrations.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxPropertiesTest {

    @Test
    void userIds_parsesCommaSeparated() {
        MaxProperties props = new MaxProperties("token", "290387676, 111");
        assertTrue(props.configured());
        assertEquals(2, props.userIds().size());
        assertEquals(290387676L, props.userIds().get(0));
    }

    @Test
    void configured_falseWithoutToken() {
        assertTrue(new MaxProperties("", "290387676").userIds().contains(290387676L));
        assertTrue(!new MaxProperties("", "290387676").configured());
    }

    @Test
    void botToken_stripsBearerAndQuotes() {
        assertEquals("abc", new MaxProperties("Bearer abc", "1").botToken());
        assertEquals("abc", new MaxProperties("\"abc\"", "1").botToken());
    }
}
