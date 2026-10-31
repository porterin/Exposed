pluginManagement {
    repositories {
        gradlePluginPortal()
        // The plugin portal no longer proxies jcenter; these host tanvd.kosogor's transitive dependencies.
        maven("https://releases.jfrog.io/artifactory/oss-releases")
        maven("https://repo.grails.org/grails/core")
    }
}
