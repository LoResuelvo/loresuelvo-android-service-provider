package com.loresuelvo.serviceprovider.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executable import boundary guard for production domain and UI sources. */
class ArchitectureImportGuardTest {

    @Test
    fun production_domain_and_ui_sources_have_no_forbidden_imports() {
        assertEquals(emptyList<String>(), violationsIn(sourceDirectory(DOMAIN_PATH), DOMAIN_FORBIDDEN))
        assertEquals(emptyList<String>(), violationsIn(sourceDirectory(UI_PATH), UI_FORBIDDEN))
    }

    @Test
    fun fixtures_prove_forbidden_imports_are_detected() {
        DOMAIN_FIXTURES.forEach { fixture ->
            assertEquals(1, forbiddenImports(resource(fixture), DOMAIN_FORBIDDEN).size)
        }
        assertEquals(1, forbiddenImports(resource("invalid-ui-data.kt"), UI_FORBIDDEN).size)
    }

    @Test
    fun fixtures_prove_legitimate_layer_imports_are_allowed() {
        assertEquals(emptyList<String>(), forbiddenImports(resource("valid-domain.kt"), DOMAIN_FORBIDDEN))
        assertEquals(emptyList<String>(), forbiddenImports(resource("valid-ui.kt"), UI_FORBIDDEN))
    }

    private fun sourceDirectory(path: String): File = File(path).also {
        check(it.isDirectory) { "Expected source directory: ${it.absolutePath}" }
    }

    private fun violationsIn(directory: File, forbiddenPrefixes: Set<String>): List<String> =
        directory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                forbiddenImports(file.readText(), forbiddenPrefixes)
                    .map { forbidden -> "${file.relativeTo(File("."))}: $forbidden" }
            }
            .toList()

    private fun resource(name: String): String = File("$RESOURCE_DIRECTORY/$name").readText()

    private fun forbiddenImports(source: String, forbiddenPrefixes: Set<String>): List<String> =
        source.lineSequence()
            .mapNotNull { line -> IMPORT_REGEX.find(line)?.groupValues?.get(1) }
            .filter { imported -> forbiddenPrefixes.any(imported::startsWith) }
            .toList()

    private companion object {
        const val DOMAIN_PATH = "src/main/java/com/loresuelvo/serviceprovider/domain"
        const val UI_PATH = "src/main/java/com/loresuelvo/serviceprovider/ui"
        const val RESOURCE_DIRECTORY = "src/test/resources/architecture-import-guard"
        val DOMAIN_FORBIDDEN = setOf(
            "android.",
            "com.loresuelvo.serviceprovider.data.",
            "com.loresuelvo.serviceprovider.ui.",
            "dagger.",
            "hilt.",
            "androidx.hilt.",
            "okhttp3.",
            "retrofit2.",
            "kotlinx.serialization.",
        )
        val UI_FORBIDDEN = setOf("com.loresuelvo.serviceprovider.data.")
        val DOMAIN_FIXTURES = listOf(
            "invalid-domain-android.kt",
            "invalid-domain-data.kt",
            "invalid-domain-ui.kt",
            "invalid-domain-dagger.kt",
            "invalid-domain-hilt.kt",
            "invalid-domain-okhttp.kt",
            "invalid-domain-retrofit.kt",
            "invalid-domain-serialization.kt",
        )
        val IMPORT_REGEX = Regex("^\\s*import\\s+([^\\s]+)")
    }
}
