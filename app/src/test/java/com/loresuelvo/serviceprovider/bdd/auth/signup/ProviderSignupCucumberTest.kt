package com.loresuelvo.serviceprovider.bdd.auth.signup

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM entry point for `provider-signup.feature`.
 *
 * The feature remains filtered by `@wip` until the Auth0 tenant's
 * provider database connection is configured and verified. Keeping a
 * dedicated runner lets the step definitions be registered now without
 * coupling them to the welcome journey runner.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth/provider-signup.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.auth.signup"],
    plugin = ["pretty", "summary"],
)
class ProviderSignupCucumberTest
