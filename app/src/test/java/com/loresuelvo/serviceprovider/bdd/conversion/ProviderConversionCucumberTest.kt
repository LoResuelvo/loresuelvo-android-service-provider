package com.loresuelvo.serviceprovider.bdd.conversion

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(features = ["classpath:features/statistics/provider-conversion.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.conversion"], tags = "not @wip", plugin = ["pretty", "summary"])
class ProviderConversionCucumberTest
