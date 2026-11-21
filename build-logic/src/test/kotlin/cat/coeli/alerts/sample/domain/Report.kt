package cat.coeli.alerts.sample.domain

import java.io.File

// Compiled into the `src/main` of the module `modules/sample` that AnalysisTest makes up.

class Report(val title: String) {
    fun render(): String = decorated()

    fun decorated(): String = "* $title *"
}

fun neverUsed(): Int = 1

fun usedByOwnTests(): Int = 2

fun usedByOtherModulesTests(): Int = 3

fun usedInModule(): Int = 4

fun readsAFile(path: String): String = File(path).name
