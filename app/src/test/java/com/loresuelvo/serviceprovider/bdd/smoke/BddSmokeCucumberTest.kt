package com.loresuelvo.serviceprovider.bdd.smoke

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM glue runner for the BDD infrastructure smoke test.
 *
 *  - Features are picked up from `classpath:features/smoke/` (the
 *    smoke folder only). Other user-journey runners
 *    (e.g. [com.loresuelvo.serviceprovider.bdd.auth.welcome.WelcomeCucumberTest])
 *    own their own folders.
 *  - Glue is restricted to `bdd.smoke` so production code never
 *    accidentally registers as a step definition source, and so a
 *    welcome / smoke step-def collision can't load twice from the
 *    same classpath.
 *  - The actual tag filter (`not @wip`) lives in
 *    `app/build.gradle.kts` via
 *    `testOptions.unitTests.all { it.systemProperty(...) }`.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/smoke"],
    glue = ["com.loresuelvo.serviceprovider.bdd.smoke"],
    plugin = ["pretty", "summary"],
)
class BddSmokeCucumberTest
