package com.loresuelvo.serviceprovider.bdd.profile

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/provider-profile.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.profile"],
    tags = "not @wip",
    plugin = ["pretty", "summary"],
)
class ProviderProfileCucumberTest
