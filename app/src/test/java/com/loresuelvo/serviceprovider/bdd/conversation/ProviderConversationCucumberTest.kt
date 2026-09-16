package com.loresuelvo.serviceprovider.bdd.conversation

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/messaging/provider-conversation.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.conversation"],
    plugin = ["pretty", "summary"],
)
class ProviderConversationCucumberTest
