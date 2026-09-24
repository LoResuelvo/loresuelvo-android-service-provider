package com.loresuelvo.serviceprovider.bdd.proposals

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/proposals/provider-service-proposal.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.proposals"],
    plugin = ["pretty", "summary"],
)
class ProviderProposalCucumberTest
