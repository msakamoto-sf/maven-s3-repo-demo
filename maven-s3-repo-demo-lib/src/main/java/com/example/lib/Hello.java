package com.example.lib;

/**
 * "Hello, World!" greeting library demo.
 */
public class Hello {
    private final String name;

    /**
     * @param name your name
     */
    public Hello(final String name) {
        this.name = name;
    }

    /**
     * @return "Hello, World!" greeting message
     */
    public String world() {
        return "Hello, World! to " + this.name + ".";
    }
}
