package com.loresuelvo.serviceprovider.bdd.auth.welcome

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM glue runner for the `provider-welcome.feature` user
 * journey. Pick up the `.feature` from the classpath (rooted at
 * `app/src/test/resources/features/`) and the step definitions from
 * the BDD package. The actual tag filter (`not @wip`) lives in
 * `app/build.gradle.kts` via
 * `testOptions.unitTests.all { it.systemProperty("cucumber.filter.tags", "not @wip") }`.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth/provider-welcome.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.auth.welcome"],
    plugin = ["pretty", "summary"],
)
class WelcomeCucumberTest