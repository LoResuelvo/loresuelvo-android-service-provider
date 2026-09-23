package com.loresuelvo.serviceprovider.bdd.profileidentity

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/provider-profile-identity.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.profileidentity"],
    tags = "not @wip",
    plugin = ["pretty", "summary"],
)
class ProviderProfileIdentityCucumberTest
