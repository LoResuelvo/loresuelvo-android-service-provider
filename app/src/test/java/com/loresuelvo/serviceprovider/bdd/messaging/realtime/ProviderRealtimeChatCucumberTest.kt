package com.loresuelvo.serviceprovider.bdd.messaging.realtime

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/messaging/provider-realtime-chat.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.messaging.realtime"],
    plugin = ["pretty", "summary"],
)
class ProviderRealtimeChatCucumberTest
