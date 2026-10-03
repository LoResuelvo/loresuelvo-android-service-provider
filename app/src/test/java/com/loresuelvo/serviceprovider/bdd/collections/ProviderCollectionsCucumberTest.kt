package com.loresuelvo.serviceprovider.bdd.collections

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(features = ["classpath:features/statistics/provider-collections.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.collections"], tags = "not @wip", plugin = ["pretty", "summary"])
class ProviderCollectionsCucumberTest
