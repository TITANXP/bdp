package org.bdp.master.server;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.Timestamp;
import java.util.LinkedList;

public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    public static ConfigurableApplicationContext getSpringBeanContext() {
        return SpringApplication.run(Main.class, new String[]{});
    }
}
