package com.loresuelvo.serviceprovider.bdd.auth.signup

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM entry point for `provider-signup.feature`.
 *
 * Scenario 01 is app-owned and exercises the configured signup request with a
 * synthetic connection. The remaining hosted tenant checks and scenarios stay
 * outside this runner's deterministic proof.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth/provider-signup.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.auth.signup"],
    plugin = ["pretty", "summary"],
)
class ProviderSignupCucumberTest
