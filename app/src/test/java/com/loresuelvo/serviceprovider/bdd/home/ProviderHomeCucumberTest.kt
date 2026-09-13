package com.loresuelvo.serviceprovider.bdd.home

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/home/provider-home.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.home"],
    plugin = ["pretty", "summary"],
)
class ProviderHomeCucumberTest
