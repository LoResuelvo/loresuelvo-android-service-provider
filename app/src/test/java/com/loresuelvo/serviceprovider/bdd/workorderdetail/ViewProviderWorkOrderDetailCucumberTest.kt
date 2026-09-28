package com.loresuelvo.serviceprovider.bdd.workorderdetail

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/workorder/view-provider-work-order-detail.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.workorderdetail"],
    plugin = ["pretty", "summary"],
)
class ViewProviderWorkOrderDetailCucumberTest
