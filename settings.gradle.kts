pluginManagement {
    repositories {
        gradlePluginPortal()
        // The plugin portal no longer proxies jcenter; these host tanvd.kosogor's transitive dependencies.
        maven("https://releases.jfrog.io/artifactory/oss-releases")
        maven("https://repo.grails.org/grails/core")
    }
}

rootProject.name = "exposed"
include("exposed-core")
include("exposed-dao")
include("exposed-jodatime")
include("exposed-java-time")
include("spring-transaction")
include("exposed-spring-boot-starter")
include("exposed-jdbc")
include("exposed-tests")
