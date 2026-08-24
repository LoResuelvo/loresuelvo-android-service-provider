package com.loresuelvo.serviceprovider.bdd.smoke

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM glue runner.
 *
 *  - Features are picked up from `classpath:features/` (rooted at
 *    `app/src/test/resources/features/`).
 *  - Glue is restricted to the BDD package so production code never
 *    accidentally registers as a step definition source.
 *  - The actual tag filter (`not @wip`) lives in `app/build.gradle.kts`
 *    via `testOptions.unitTests.all { it.systemProperty(...) }`. Cucumber
 *    inherits it through the `cucumber.filter.tags` system property.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features"],
    glue = ["com.loresuelvo.serviceprovider.bdd"],
    plugin = ["pretty", "summary"],
)
class BddSmokeCucumberTest
