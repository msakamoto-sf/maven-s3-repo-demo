package com.example.lib;

import org.junit.Assert;
import org.junit.Test;

public class HelloTest {
    @Test
    public void testHello() {
        final Hello hello = new Hello("foo");
        Assert.assertEquals("Hello, World! to foo.", hello.world());
    }
}
