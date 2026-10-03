package com.loresuelvo.serviceprovider.bdd.statistics

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(features = ["classpath:features/statistics/provider-activity.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.statistics"], tags = "not @wip", plugin = ["pretty", "summary"])
class ProviderActivityCucumberTest
