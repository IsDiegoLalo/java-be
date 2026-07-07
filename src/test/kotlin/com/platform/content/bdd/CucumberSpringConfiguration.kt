package com.platform.content.bdd

import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort

/**
 * Spring context configuration for Cucumber BDD tests.
 * Starts the full application with a random port for integration testing.
 *
 * When Docker/Testcontainers are available, this context will connect to
 * PostgreSQL, MongoDB, and Kafka containers for full-stack testing.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CucumberSpringConfiguration {

    @LocalServerPort
    var port: Int = 0
}
