package com.loresuelvo.serviceprovider.bdd.reputation

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(features = ["classpath:features/statistics/provider-reputation.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.reputation"], tags = "not @wip", plugin = ["pretty", "summary"])
class ProviderReputationCucumberTest
