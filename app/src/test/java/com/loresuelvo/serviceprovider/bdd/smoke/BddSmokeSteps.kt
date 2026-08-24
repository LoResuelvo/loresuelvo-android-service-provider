package com.loresuelvo.serviceprovider.bdd.smoke

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * BDD smoke steps. The body is intentionally no-op:
 *  - `Given` proves that step definitions are wired to the glue package.
 *  - `When`  proves that Cucumber loads features from the classpath.
 *  - `Then`  proves that no step is left undefined — Cucumber would have
 *    failed the run before reaching this point otherwise.
 *
 * Real user-journey step definitions land in Fase 1 (walking skeleton).
 */
class BddSmokeSteps {

    @Given("que el pipeline BDD está configurado")
    fun theBddPipelineIsConfigured() {
        // No-op: wiring the @Given annotation already proves glue configuration.
    }

    @When("se carga el feature de smoke")
    fun theSmokeFeatureIsLoaded() {
        // No-op: reaching this step means Cucumber parsed and loaded the .feature.
    }

    @Then("el runner termina sin pasos sin definir")
    fun theRunnerFinishesWithoutUndefinedSteps() {
        // No-op: Cucumber throws `UndefinedStepException` before invoking `Then`
        // when any previous step lacks a matching definition.
    }
}
