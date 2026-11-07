plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

val ktlintVersion = libs.versions.ktlint.get()

// Spotless doesn't pass .editorconfig through to ktlint, so the settings are given here too.
// Keep in sync with .editorconfig, which editors and the ktlint CLI read.
val ktlintSettings =
    mapOf(
        // Matches kotlin.code.style=official in gradle.properties.
        "ktlint_code_style" to "intellij_idea",
        "max_line_length" to "120",
        // Composables are named like types.
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        // Off: it splits short catalog lookups like libs.versions.android.minSdk.get().toInt()
        // over five lines.
        "ktlint_standard_chain-method-continuation" to "disabled",
    )

// Dependencies F-Droid treats as non-free, as regexes matched against "group:module:version".
// Our own list of the main offenders. F-Droid's scanner checks its full list when it builds.
val nonFreeDependencies =
    listOf(
        """^com\.google\.android\.gms[.:]""", // Play Services
        """^com\.google\.android\.ump[.:]""", // User Messaging Platform (ads consent)
        """^com\.google\.firebase[.:]""",
        """^com\.firebase[.:]""",
        """^com\.google\.android\.play[.:]""", // Play Core, in-app updates and reviews
        """^com\.google\.android\.libraries[.:]""", // Places and other closed SDKs
        """^com\.google\.android\.recaptcha[.:]""",
        """^com\.google\.mlkit[.:]""",
        """^com\.google\.ads[.:]""",
        """^com\.google\.android\.wearable[.:]""",
        """^com\.android\.billingclient[.:]""", // Play Billing
        """^com\.android\.installreferrer[.:]""",
        """^com\.huawei\.hms[.:]""",
        """^com\.crashlytics[.:]""",
        """^io\.fabric[.:]""",
        """^com\.facebook\.android[.:]""",
        """^com\.amazon\.device[.:]""",
        """^com\.onesignal[.:]""",
        """^androidx\.core:core-google-shortcuts(:|$)""",
        """^androidx\.credentials:credentials-play-services""",
        """^androidx\.work:work-gcm(:|$)""",
        """^androidx\.media3:media3-exoplayer-ima(:|$)""",
        """^com\.google\.android\.exoplayer:extension-ima(:|$)""",
    )

/** Fails if any resolved dependency matches a non-free pattern. */
abstract class CheckNonFreeDependencies : DefaultTask() {
    @get:Input
    abstract val modules: SetProperty<String>

    @get:Input
    abstract val patterns: ListProperty<String>

    @TaskAction
    fun check() {
        val regexes = patterns.get().map { Regex(it, RegexOption.IGNORE_CASE) }
        val found = modules.get().filter { dep -> regexes.any { it.containsMatchIn(dep) } }.sorted()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Non-free dependencies, which F-Droid won't build:\n" + found.joinToString("\n") { "  $it" },
            )
        }
    }
}

fun ResolvedComponentResult.allModules(): Set<String> {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val modules = mutableSetOf<String>()

    fun visit(component: ResolvedComponentResult) {
        if (!seen.add(component)) return
        (component.id as? ModuleComponentIdentifier)?.let { modules += "${it.group}:${it.module}:${it.version}" }
        component.dependencies.forEach { dependency ->
            when (dependency) {
                is ResolvedDependencyResult -> visit(dependency.selected)
                is UnresolvedDependencyResult -> modules += dependency.attempted.displayName
            }
        }
    }
    visit(this)
    return modules
}

subprojects {
    val checkNonFree =
        tasks.register<CheckNonFreeDependencies>("checkNonFreeDependencies") {
            group = "verification"
            description = "Fails if a dependency F-Droid treats as non-free is on any classpath."
            patterns.set(nonFreeDependencies)
            // Every resolvable classpath: compile, runtime, annotation processors, KSP, tests.
            configurations
                .matching { it.isCanBeResolved && it.name.endsWith("Classpath") }
                .all {
                    modules.addAll(incoming.resolutionResult.rootComponent.map { it.allModules() })
                }
        }
    // In check, and before any Android build, so assembleDebug alone can't package a non-free module.
    tasks.matching { it.name == "check" || it.name == "preBuild" }.configureEach { dependsOn(checkNonFree) }

    apply(plugin = "dev.detekt")

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        // Every source set, including iosMain when the iOS targets are off. Without this,
        // the plain detekt task finds no sources in a multiplatform module.
        source.setFrom(files("src"))
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        if (this@allprojects != rootProject) {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion).editorConfigOverride(ktlintSettings)
            }
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).editorConfigOverride(ktlintSettings)
        }
    }
}
