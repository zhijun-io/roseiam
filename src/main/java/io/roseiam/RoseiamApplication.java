package io.roseiam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 1)
@MapperScan({
    "io.roseiam.audit",
    "io.roseiam.identity",
    "io.roseiam.platformsetting",
    "io.roseiam.role",
    "io.roseiam.user"
})
public class RoseiamApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoseiamApplication.class, args);
    }
}
