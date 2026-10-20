package io.roseiam;

import org.springframework.boot.SpringApplication;

public class TestRoseiamApplication {

    public static void main(String[] args) {
        SpringApplication.from(RoseiamApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
