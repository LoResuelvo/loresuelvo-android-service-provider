package com.loresuelvo.serviceprovider.bdd.notifications

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/notifications/provider-push-notifications.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.notifications"],
    plugin = ["pretty", "summary"],
)
class ProviderNotificationCucumberTest
