package com.loresuelvo.serviceprovider.bdd.turns

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/turns/view-provider-turns.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.turns"],
    plugin = ["pretty", "summary"],
)
class ProviderTurnsCucumberTest
