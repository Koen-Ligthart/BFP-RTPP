plugins {
    kotlin("jvm") version "1.6.10"
    java
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.lets-plot:lets-plot-common:2.2.1")
    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.1.1")
    implementation(files("C:/gurobi900/win64/lib/gurobi.jar"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}