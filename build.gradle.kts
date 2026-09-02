import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("org.gradlex.maven-plugin-development") version "1.0.3"
    `maven-publish`
    signing
}

group = "io.fabrikt"
val fabriktVersion = providers.gradleProperty("fabriktVersion").get()
version = fabriktVersion
description = "Official Maven plugin for Fabrikt code generation"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

val mavenVersion = "3.9.16"
val junitVersion = "5.9.2"
val pluginDescriptorDependencies by configurations.creating {
    isTransitive = false
}
val mavenDistribution by configurations.creating

val integrationTestSourceSet = sourceSets.create("integrationTest")
configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    compileOnly("org.apache.maven:maven-plugin-api:$mavenVersion")
    compileOnly("org.apache.maven:maven-core:$mavenVersion")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.15.2")

    runtimeOnly("io.fabrikt:fabrikt:$fabriktVersion")
    pluginDescriptorDependencies("io.fabrikt:fabrikt:$fabriktVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.apache.maven:maven-core:$mavenVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    mavenDistribution("org.apache.maven:apache-maven:$mavenVersion:bin@zip")
}

mavenPlugin {
    goalPrefix.set("fabrikt")
    dependencies.set(pluginDescriptorDependencies)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val extractMaven by tasks.registering(Sync::class) {
    from(provider { zipTree(mavenDistribution.singleFile) })
    into(layout.buildDirectory.dir("maven-distribution"))
}

val integrationTestRepository = layout.buildDirectory.dir("integration-test-repository")

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the Maven plugin against real Maven consumer projects."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    dependsOn(
        tasks.withType<PublishToMavenRepository>().matching {
            it.repository.name == "integrationTest"
        },
        extractMaven,
    )
    shouldRunAfter(tasks.test)
    systemProperty("fabrikt.plugin.version", project.version.toString())
    systemProperty("fabrikt.test.repository", integrationTestRepository.get().asFile.toURI())
    systemProperty(
        "fabrikt.maven.home",
        layout.buildDirectory.dir("maven-distribution/apache-maven-$mavenVersion").get().asFile,
    )
}

tasks.check {
    dependsOn(integrationTest)
}

publishing {
    repositories {
        maven {
            name = "integrationTest"
            url = uri(integrationTestRepository)
        }
        maven {
            name = "centralStaging"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USER_TOKEN_USERNAME")
                password = System.getenv("OSSRH_USER_TOKEN_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenPlugin") {
            from(components["java"])

            pom {
                name.set("Fabrikt Maven Plugin")
                description.set(project.description)
                url.set("https://github.com/fabrikt-io/fabrikt-maven-plugin")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("cjbooms")
                        name.set("Conor Gallagher")
                        email.set("cjbooms@gmail.com")
                    }
                    developer {
                        id.set("averabaq")
                        name.set("Alejandro Vera-Baquero")
                        email.set("averabaq@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/fabrikt-io/fabrikt-maven-plugin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/fabrikt-io/fabrikt-maven-plugin.git")
                    url.set("https://github.com/fabrikt-io/fabrikt-maven-plugin")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/fabrikt-io/fabrikt-maven-plugin/issues")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenPlugin"])
    }
}
