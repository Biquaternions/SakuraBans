import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The version of a build: the base version of `gradle.properties` plus the release channel
 * (`-Pchannel=`, default from `gradle.properties`) and an optional build number
 * (`-PbuildNumber=`): `1.0.0-dev`, `1.0.0-alpha.3`, and `1.0.0` on the `release` channel.
 */
data class ReleaseVersion(val base: String, val channel: String, val buildNumber: String) {

    val full: String
        get() = when {
            channel == "release" -> base
            buildNumber.isEmpty() -> "$base-$channel"
            else -> "$base-$channel.$buildNumber"
        }

    companion object {
        val CHANNELS = listOf("dev", "alpha", "beta", "rc", "release")

        fun of(project: Project): ReleaseVersion {
            val providers = project.providers
            val base = providers.gradleProperty("version").get()
            val channel = providers.gradleProperty("channel").getOrElse("dev")
            val buildNumber = providers.gradleProperty("buildNumber").getOrElse("")
            if (channel !in CHANNELS) {
                throw GradleException("Unknown release channel '$channel': use one of $CHANNELS")
            }
            if (buildNumber.isNotEmpty() && buildNumber.toIntOrNull()?.takeIf { it >= 0 } == null) {
                throw GradleException("buildNumber must be a number, not '$buildNumber'")
            }
            if (channel == "release" && buildNumber.isNotEmpty()) {
                throw GradleException("A build on the release channel has no build number")
            }
            return ReleaseVersion(base, channel, buildNumber)
        }
    }
}
