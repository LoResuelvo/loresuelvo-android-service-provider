package com.loresuelvo.serviceprovider.bdd.paymentaccount

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features/paymentaccount/connect-mercado-pago.feature"],
    glue = ["com.loresuelvo.serviceprovider.bdd.paymentaccount"],
    plugin = ["pretty", "summary"],
)
class ConnectMercadoPagoCucumberTest
