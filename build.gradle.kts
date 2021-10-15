plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.16.0"
}

group = "org.glavo"
version = "1.2-SNAPSHOT"

repositories {
    mavenCentral()
}

val jabel = configurations.create("jabel")

dependencies {
    implementation(gradleApi())

    jabel("com.github.bsideup.jabel:jabel-javac-plugin:0.4.1")
    jabel("net.java.dev.jna:jna-platform:5.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.compileJava {
    options.release.set(8)
}

java {
    withSourcesJar()
}

tasks.jar {
    val listFile = File(buildDir, "list.txt")

    doFirst {
        listFile.printWriter().use { writer ->
            for (file in jabel.files) {
                writer.println(file.name + ":" + file.length())
            }
        }
    }

    into("kala/plugins/retro8/jabel") {
        from(jabel)
        from(listFile)
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("kala-retro8") {
            id = "org.glavo.kala-retro8"
            displayName = "Kala Retro8"
            description = "Make Java 8 Great Again"
            implementationClass = "kala.plugins.retro8.Retro8Plugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/Glavo/kala-retro8"
    vcsUrl = "https://github.com/Glavo/kala-retro8.git"
    tags = listOf("java", "modules", "jpms", "modularity")
}

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        this.create<MavenPublication>("pluginMaven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
}