package com.loresuelvo.serviceprovider.bdd.identity

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/identity/optional-provider-identity-verification.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.identity"],
    plugin = ["pretty", "summary"],
)
class OptionalIdentityVerificationCucumberTest
