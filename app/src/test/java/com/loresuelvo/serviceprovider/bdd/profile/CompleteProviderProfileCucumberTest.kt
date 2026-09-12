package com.loresuelvo.serviceprovider.bdd.profile

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM entry point for `complete-provider-profile.feature`.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/complete-provider-profile.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.profile"],
    plugin = ["pretty", "summary"],
)
class CompleteProviderProfileCucumberTest
