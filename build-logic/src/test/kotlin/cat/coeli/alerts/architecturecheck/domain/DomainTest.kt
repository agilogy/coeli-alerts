package cat.coeli.alerts.architecturecheck.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayerTest {
    @Test
    fun theDeepestLayerSegmentNamesThePackagesLayer() {
        val layers =
            listOf(
                    "a.domain.alerts",
                    "a.infrastructure.postgres",
                    "a.domain.infrastructure",
                    "a.utils",
                )
                .map { layerOf(it) }

        assertEquals(
            listOf(Layer.Domain, Layer.Infrastructure, Layer.Infrastructure, Layer.Helper),
            layers,
        )
    }

    @Test
    fun aLayerMayDependOnItselfAndOnTheLayersAboveIt() {
        val allowed =
            Layer.entries.associateWith { origin ->
                Layer.entries.filter { origin.mayDependOn(it) }
            }

        assertEquals(
            mapOf(
                Layer.Helper to listOf(Layer.Helper),
                Layer.Domain to listOf(Layer.Helper, Layer.Domain),
                Layer.Infrastructure to Layer.entries,
            ),
            allowed,
        )
    }

    @Test
    fun onlyInfrastructureThatNeedsNothingOfItIsPlacedTooLow() {
        assertTrue(isPlacedTooLow(Layer.Infrastructure, Layer.Domain))
        assertFalse(isPlacedTooLow(Layer.Infrastructure, Layer.Infrastructure))
        assertFalse(isPlacedTooLow(Layer.Domain, Layer.Helper))
    }
}

class CatalogueTest {
    private val catalogue =
        Catalogue(
            mapOf(
                "java" to Layer.Helper,
                "java.lang.System" to Layer.Infrastructure,
                "java.lang.System#lineSeparator" to Layer.Helper,
                "kotlin.random.Random" to Layer.Infrastructure,
            ),
        )

    @Test
    fun theMostPreciseEntryWins() {
        val layers =
            listOf(
                catalogue.libraryLayerOf("java.lang.String", "java.lang"),
                catalogue.libraryLayerOf("java.lang.System", "java.lang", "getenv"),
                catalogue.libraryLayerOf("java.lang.System", "java.lang", "lineSeparator"),
                catalogue.libraryLayerOf("kotlin.random.Random\$Default", "kotlin.random"),
            )

        assertEquals(
            listOf(Layer.Helper, Layer.Infrastructure, Layer.Helper, Layer.Infrastructure),
            layers,
        )
    }

    @Test
    fun aLibraryNoEntryCoversHasNoLayer() {
        assertNull(catalogue.libraryLayerOf("org.example.Thing", "org.example"))
    }
}

class GraphsTest {
    @Test
    fun cyclesAreTheGroupsOfNodesThatReachEachOther() {
        val cycles =
            cyclesOf(
                mapOf(
                    "a" to setOf("b"),
                    "b" to setOf("c", "d"),
                    "c" to setOf("a"),
                    "d" to setOf("e"),
                    "e" to setOf("d"),
                ),
            )

        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e")), cycles)
    }

    @Test
    fun aGraphWithoutCyclesHasNone() {
        assertEquals(emptyList(), cyclesOf(mapOf("a" to setOf("b"), "b" to setOf("c"))))
    }

    @Test
    fun aClassNeedsTheDeepestLayerOfWhatItReachesDirectlyOrNot() {
        val needed =
            neededLayersOf(
                libraryLayers = mapOf("adapter" to Layer.Infrastructure, "rule" to Layer.Domain),
                dependencies =
                    mapOf(
                        "service" to setOf("rule"),
                        "wiring" to setOf("service", "adapter"),
                        "util" to emptySet(),
                    ),
            )

        assertEquals(
            mapOf(
                "adapter" to Layer.Infrastructure,
                "rule" to Layer.Domain,
                "service" to Layer.Domain,
                "wiring" to Layer.Infrastructure,
                "util" to Layer.Helper,
            ),
            needed,
        )
    }
}

class VisibilityTest {
    private fun verdict(
        current: Visibility = Visibility.Public,
        uses: Set<UseScope>,
        overriders: Set<UseScope> = emptySet(),
        canBePrivate: Boolean = true,
        isApp: Boolean = false,
    ) = verdictOf(current, uses, overriders, canBePrivate, isApp)

    @Test
    fun aMemberUsedOnlyWhereItIsDeclaredCanBePrivate() {
        assertEquals(
            Verdict.TooVisible(Visibility.Public, Visibility.Private),
            verdict(uses = setOf(UseScope.Declaration)),
        )
    }

    @Test
    fun anOpenMemberCanBeNoNarrowerThanInternal() {
        assertEquals(
            Verdict.TooVisible(Visibility.Public, Visibility.Internal),
            verdict(uses = setOf(UseScope.Declaration), canBePrivate = false),
        )
    }

    @Test
    fun aMemberOverriddenInAnotherModuleMustBePublic() {
        assertNull(verdict(uses = setOf(UseScope.Module), overriders = setOf(UseScope.OtherModule)))
    }

    @Test
    fun inAnAppPublicIsAsGoodAsInternal() {
        assertNull(verdict(uses = setOf(UseScope.Module), isApp = true))
    }

    @Test
    fun aMemberAsNarrowAsItsUsesNeedIsFine() {
        assertNull(
            verdict(
                current = Visibility.Internal,
                uses = setOf(UseScope.Module, UseScope.Declaration),
            ),
        )
    }

    @Test
    fun aMemberOnlyOtherModulesTestsUseIsUnused() {
        assertEquals(
            Verdict.Unused(onlyTestsOfOtherModules = true),
            verdict(uses = setOf(UseScope.OtherModuleTest)),
        )
        assertEquals(Verdict.Unused(onlyTestsOfOtherModules = false), verdict(uses = emptySet()))
    }
}

class SourceTextsTest {
    @Test
    fun aModulesRootPackageIsItsDirectoryNameWithoutDashes() {
        assertEquals("cat.coeli.alerts.testutils", rootPackageOf("modules/test-utils"))
    }

    @Test
    fun aPackageOutsideTheRootIsCutWhereItLeavesIt() {
        val outside =
            packagesOutsideRootOf(
                "modules/weather",
                setOf(
                    "cat.coeli.alerts.weather.domain",
                    "cat.coeli.alerts.other.domain",
                    "cat.coeli.alerts.other.x",
                    "org.x",
                ),
            )

        assertEquals(listOf("cat.coeli.alerts.other", "org"), outside)
    }

    @Test
    fun stringDependenciesAreNamedWithoutTheirVersion() {
        val found =
            stringDependenciesOf(
                """
                dependencies {
                    implementation(libs.ktor.client.core)
                    implementation(platform("org.junit:junit-bom:6.0.0"))
                    // implementation("com.example:commented-out:1.0")
                    testImplementation(testFixtures(project(":alertsrepository")))
                    jvmArgs("--enable-native-access=ALL-UNNAMED")
                }
                """
                    .trimIndent(),
            )

        assertEquals(
            listOf(
                TextFinding("implementation org.junit:junit-bom", 3),
                TextFinding("project :alertsrepository", 5),
            ),
            found,
        )
    }

    @Test
    fun everySuppressionButTheAllowedOnesIsForbidden() {
        val found =
            forbiddenSuppressionsOf(
                """
                @file:Suppress("MagicNumber")
                // @Suppress("Commented")
                @Suppress("Allowed", "detekt:LongMethod")
                fun x() = Unit
                """
                    .trimIndent(),
                allowed = setOf("Allowed"),
            )

        assertEquals(
            listOf(TextFinding("MagicNumber", 1), TextFinding("detekt:LongMethod", 3)),
            found,
        )
    }
}
