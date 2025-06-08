package com.eipresso.user;

import com.eipresso.user.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.cloud.consul.discovery.enabled=false",
    "spring.cloud.consul.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.application.name=user-service-test",
    "spring.main.allow-bean-definition-overriding=true",
    "camel.springboot.main-run-controller=false",
    "camel.springboot.routes-include-pattern=",
    "camel.springboot.routes-exclude-pattern=**",
    "hazelcast.enabled=false"
})
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
        // Test that the application context loads successfully
    }
} 