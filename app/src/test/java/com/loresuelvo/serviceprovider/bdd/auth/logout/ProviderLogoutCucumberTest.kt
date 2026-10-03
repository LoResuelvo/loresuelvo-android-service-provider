package com.loresuelvo.serviceprovider.bdd.auth.logout

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/auth/provider-logout.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.auth.logout"],
    plugin = ["pretty", "summary"],
)
class ProviderLogoutCucumberTest
