package com.loresuelvo.serviceprovider.bdd.workorder

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/workorder/report-provider-work-completion.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.workorder"],
    plugin = ["pretty", "summary"],
)
class ReportProviderWorkCompletionCucumberTest
