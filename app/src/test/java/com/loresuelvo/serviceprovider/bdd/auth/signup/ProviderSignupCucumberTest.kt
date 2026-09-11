package com.loresuelvo.serviceprovider.bdd.auth.signup

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM entry point for `provider-signup.feature`.
 *
 * Scenario 01 covers the app-owned delegation and signup request shape. The
 * tenant-selected connection and remaining hosted checks stay outside this
 * runner's deterministic proof.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth/provider-signup.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.auth.signup"],
    plugin = ["pretty", "summary"],
)
class ProviderSignupCucumberTest
