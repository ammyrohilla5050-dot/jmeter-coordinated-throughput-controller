plugins {
    `java-library`
    `maven-publish`
}

group = "org.apache.jmeter.plugins"
version = "0.1.1"
description = "Coordinated Throughput Controller for Apache JMeter"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

val jmeterVersion = providers.gradleProperty("jmeterVersion").getOrElse("5.6.3")
val jmeterSourceHome = providers.gradleProperty("jmeterSourceHome")
    .map { file(it) }
    .getOrElse(layout.projectDirectory.dir("../..").asFile)

val localJMeterJars = listOf(
    "src/core/build/libs/ApacheJMeter_core-5.6.3-SNAPSHOT.jar",
    "src/components/build/libs/ApacheJMeter_components-5.6.3-SNAPSHOT.jar",
    "src/jorphan/build/libs/jorphan-5.6.3-SNAPSHOT.jar",
).map { jmeterSourceHome.resolve(it) }

val useLocalJMeter = localJMeterJars.all { it.isFile }

fun DependencyHandlerScope.jmeterArtifacts(configurationName: String) {
    if (useLocalJMeter) {
        add(configurationName, files(localJMeterJars))
    } else {
        add(configurationName, "org.apache.jmeter:ApacheJMeter_core:$jmeterVersion")
        add(configurationName, "org.apache.jmeter:ApacheJMeter_components:$jmeterVersion")
        add(configurationName, "org.apache.jmeter:jorphan:$jmeterVersion")
    }
}

dependencies {
    jmeterArtifacts("compileOnly")
    compileOnly("org.slf4j:slf4j-api:1.7.36")
    compileOnly("com.miglayout:miglayout-swing:5.3")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    compileOnly("org.apiguardian:apiguardian-api:1.1.2")

    jmeterArtifacts("testImplementation")
    testImplementation("org.slf4j:slf4j-api:1.7.36")
    testImplementation("com.miglayout:miglayout-swing:5.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    testRuntimeOnly("oro:oro:2.0.8")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("jmeter-coordinated-throughput-controller")
    manifest {
        attributes(
            "Implementation-Title" to "Coordinated Throughput Controller",
            "Implementation-Version" to project.version,
            "JMeter-Plugin-Name" to "Coordinated Throughput Controller",
            "JMeter-Plugin-Class" to "org.apache.jmeter.control.CoordinatedThroughputController",
        )
    }
}

val releaseJar by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("dist"))
}

val releaseZip by tasks.registering(Zip::class) {
    dependsOn(releaseJar)
    archiveBaseName.set("jmeter-coordinated-throughput-controller")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
    from(layout.projectDirectory.dir("dist")) {
        include("jmeter-coordinated-throughput-controller-${project.version}.jar")
    }
    from(layout.projectDirectory) {
        include("README.md", "PUBLISHING.md", "LICENSE", "NOTICE")
    }
    from(layout.projectDirectory.dir("docs")) {
        into("docs")
    }
    from(layout.projectDirectory.dir("repo")) {
        into("repo")
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            from(components["java"])
            artifactId = "jmeter-coordinated-throughput-controller"
            pom {
                name.set("Coordinated Throughput Controller")
                description.set(project.description)
                url.set("https://github.com/ammyrohilla5050-dot/jmeter-coordinated-throughput-controller")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
