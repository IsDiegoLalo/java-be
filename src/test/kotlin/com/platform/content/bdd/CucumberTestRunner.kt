package com.platform.content.bdd

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Cucumber test runner that bootstraps BDD tests using JUnit 5 Platform Suite.
 * Scans for feature files in the classpath and delegates to step definitions
 * annotated with Cucumber annotations.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue", value = "com.platform.content.bdd")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty, html:build/reports/cucumber/cucumber-report.html")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
class CucumberTestRunner
