plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation("dev.inmo:krontab:0.7.1")
    implementation("io.github.microutils:kotlin-logging:1.12.5")
    implementation("ch.qos.logback:logback-classic:1.2.6")
    implementation("io.github.resilience4j:resilience4j-kotlin:1.7.1")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:1.7.1")

    api(project(":pleo-antaeus-models"))
}