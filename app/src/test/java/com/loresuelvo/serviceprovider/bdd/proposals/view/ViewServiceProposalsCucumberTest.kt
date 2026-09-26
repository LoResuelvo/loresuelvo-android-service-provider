package com.loresuelvo.serviceprovider.bdd.proposals.view

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/proposals/view-service-proposals.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.proposals.view"],
    plugin = ["pretty", "summary"],
    tags = "not @wip",
)
class ViewServiceProposalsCucumberTest
