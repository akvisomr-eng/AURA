plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":aura-core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.aura.desktop.AuraDesktopKt")
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
