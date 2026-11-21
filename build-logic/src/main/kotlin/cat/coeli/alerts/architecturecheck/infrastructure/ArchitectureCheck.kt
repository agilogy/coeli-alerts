package cat.coeli.alerts.architecturecheck.infrastructure

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Checks the architecture of the whole repository and writes the report as `report.md`,
 * `report.html` and `report.txt`, failing on any finding: there is no baseline, every finding is to
 * be fixed.
 */
@DisableCachingByDefault(
    because = "It reads every module's classes, and fails rather than produce a result to reuse",
)
abstract class ArchitectureCheck : DefaultTask() {
    @get:Internal abstract val repositoryRoot: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogue: RegularFileProperty

    /**
     * The classes, sources and build scripts the check reads, so that it is up to date while none
     * changes.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val analysedFiles: ConfigurableFileCollection

    /** Where each file of the repository can be seen, such as the commit on GitHub. */
    @get:Input @get:Optional abstract val sourceLinkBase: Property<String>

    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile
        val findings = analyze(root, readCatalogue(catalogue.get().asFile.readText()))
        val report = Report(findings, sourceLinkBase.orNull)
        val directory = reportDirectory.get().asFile
        directory.resolve("report.md").writeText(report.markdown())
        directory.resolve("report.txt").writeText(report.text())
        val html = directory.resolve("report.html").apply { writeText(report.html()) }
        if (findings.isNotEmpty()) {
            logger.error(report.text())
            throw GradleException(
                "The architecture check found ${findings.size} issues: see ${html.toURI()}",
            )
        }
    }
}
