package com.loresuelvo.serviceprovider.bdd.jobrequest

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/jobrequest/respond-job-request.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.jobrequest"],
    plugin = ["pretty", "summary"],
)
class JobRequestCucumberTest
