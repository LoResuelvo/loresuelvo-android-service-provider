package com.loresuelvo.serviceprovider.bdd.coverage

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/provider-coverage-zones.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.coverage"],
    plugin = ["pretty", "summary"],
)
class ProviderCoverageZonesCucumberTest
