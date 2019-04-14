package com.example.client;

import com.example.lib.Hello;

public class Main {
    public static void main(final String[] args) {
        final Hello hello = new Hello("abc");
        System.out.println(hello.world());
    }
}
