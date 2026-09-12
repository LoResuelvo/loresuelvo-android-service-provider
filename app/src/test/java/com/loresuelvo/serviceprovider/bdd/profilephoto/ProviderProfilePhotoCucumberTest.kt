package com.loresuelvo.serviceprovider.bdd.profilephoto

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/**
 * Cucumber JVM entry point for `provider-profile-photo.feature`.
 */
@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/profile/provider-profile-photo.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.profilephoto"],
    plugin = ["pretty", "summary"],
)
class ProviderProfilePhotoCucumberTest
