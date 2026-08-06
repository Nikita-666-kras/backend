package com.blog.platform.integrations.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoArServiceTest {

    @Test
    void extractAr_stripsNonDigitsAndTakesLastFour() {
        assertEquals("4567", AutoArService.extractAr("+7 (999) 123-45-67"));
        assertEquals("4567", AutoArService.extractAr("89991234567"));
        assertEquals("67", AutoArService.extractAr("67"));
        assertEquals("", AutoArService.extractAr("abc"));
        assertEquals("", AutoArService.extractAr(null));
        assertEquals("", AutoArService.extractAr("   "));
    }
}
