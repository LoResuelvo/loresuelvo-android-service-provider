package com.loresuelvo.serviceprovider.bdd.calendar

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/provider-calendar.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.calendar"],
    tags = "not @wip",
    plugin = ["pretty", "summary"],
)
class ProviderCalendarCucumberTest
