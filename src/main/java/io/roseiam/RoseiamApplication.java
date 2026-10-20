package io.roseiam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
    "io.roseiam.audit",
    "io.roseiam.identity.mapper",
    "io.roseiam.platformsetting",
    "io.roseiam.persistence.mapper",
    "io.roseiam.role",
    "io.roseiam.user.mapper",
    "io.roseiam.user.persistence"
})
public class RoseiamApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoseiamApplication.class, args);
    }
}
