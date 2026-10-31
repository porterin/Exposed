repositories {
    jcenter()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    gradleApi()
    // 0.9.3 was only published to the sunset jcenter; 0.14.2 is the oldest version still hosted.
    compile("com.avast.gradle", "gradle-docker-compose-plugin", "0.14.2")
}

plugins {
    `kotlin-dsl` apply true
    id("tanvd.kosogor") version "1.0.7" apply true
}
