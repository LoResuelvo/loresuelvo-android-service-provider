package com.loresuelvo.serviceprovider.bdd.messaging

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/messaging/provider-message-inbox.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.messaging"],
    plugin = ["pretty", "summary"],
)
class ProviderMessageInboxCucumberTest
