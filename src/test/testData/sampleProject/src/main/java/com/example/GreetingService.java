package com.example;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String hello(String name) {
        return formatGreeting("Hello", name);
    }

    private String formatGreeting(String prefix, String name) {
        return prefix + ", " + name + "!";
    }
}
