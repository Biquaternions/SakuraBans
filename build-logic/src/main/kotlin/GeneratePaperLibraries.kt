import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Writes `paper-libraries.txt`: one `group:artifact:version` per line for every
 * external module that one of our own projects depends on directly. Paper's
 * `MavenLibraryResolver` resolves their transitive dependencies at runtime.
 */
@CacheableTask
abstract class GeneratePaperLibraries : DefaultTask() {

    @get:Input
    abstract val libraries: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.file(FILE_NAME).get().asFile
        file.parentFile.mkdirs()
        file.writeText(libraries.get().joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        const val FILE_NAME = "paper-libraries.txt"

        /** The external modules our projects declare, with the versions Gradle resolved. */
        fun directLibraries(root: ResolvedComponentResult): List<String> {
            val libraries = sortedSetOf<String>()
            val visited = mutableSetOf<ResolvedComponentResult>()
            fun visit(component: ResolvedComponentResult) {
                if (!visited.add(component)) {
                    return
                }
                for (dependency in component.dependencies) {
                    if (dependency !is ResolvedDependencyResult) {
                        continue
                    }
                    val selected = dependency.selected
                    when (val id = selected.id) {
                        is ProjectComponentIdentifier -> visit(selected)
                        is ModuleComponentIdentifier ->
                            libraries.add("${id.group}:${id.module}:${id.version}")
                    }
                }
            }
            visit(root)
            return libraries.toList()
        }
    }
}
