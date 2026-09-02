# Fabrikt Maven Plugin

The official Maven plugin for [Fabrikt](https://github.com/fabrikt-io/fabrikt), the Kotlin code generator for OpenAPI 3 specifications.

The plugin and Fabrikt use the same version number. The plugin resolves and runs the matching `io.fabrikt:fabrikt` executable JAR in a separate Java process, so a plugin release never silently selects a different generator version.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

## Usage

Configure one execution for each OpenAPI specification:

```xml
<properties>
    <fabrikt.version>27.8.0</fabrikt.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>io.fabrikt</groupId>
            <artifactId>fabrikt-maven-plugin</artifactId>
            <version>${fabrikt.version}</version>
            <configuration>
                <arguments>
                    <serializationLibrary>JACKSON_3</serializationLibrary>
                </arguments>
            </configuration>
            <executions>
                <execution>
                    <id>generate-customer-api</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <inputFile>src/main/openapi/customer.yaml</inputFile>
                        <arguments>
                            <basePackage>com.example.customer</basePackage>
                            <targets>
                                <value>http_models</value>
                                <value>client</value>
                            </targets>
                        </arguments>
                    </configuration>
                </execution>
                <execution>
                    <id>generate-order-api</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <inputFile>src/main/openapi/order.yaml</inputFile>
                        <arguments>
                            <basePackage>com.example.order</basePackage>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Names inside `arguments` may be written in camelCase or with Fabrikt's existing kebab-case CLI names. Plugin-level arguments are shared defaults; execution-level values override them. Repeatable options use nested `value` elements, as shown for `targets` above. See Fabrikt's [configuration options](https://github.com/fabrikt-io/fabrikt#configuration-options) for the supported arguments.

Binding an execution to `generate-sources` runs generation in Maven's normal lifecycle. Leave out the phase when generation should only happen explicitly:

```shell
mvn fabrikt:generate@generate-customer-api
```

`inputFile` accepts a path relative to the Maven project or an HTTP(S) URL. The default output directory is `${project.build.directory}/generated-sources`. After successful generation, existing `src/main/kotlin` and `src/test/kotlin` directories below it are registered as Maven source roots.

The plugin does not delete generated files or maintain a separate incremental cache. Use Maven's `clean` lifecycle when clean generation is required. A non-zero Fabrikt exit code fails the Maven build.

## Development

The build requires JDK 17 and uses the Gradle wrapper:

```shell
./gradlew clean build
```

`build` runs unit tests and integration tests against real Maven consumer projects. The integration tests publish the plugin to an isolated local repository, resolve the matching released Fabrikt artifact, generate Kotlin from multiple specifications, and compile the generated sources.

Override the Fabrikt/plugin version for compatibility or release verification with `-PfabriktVersion=<version>`. Both the plugin publication and its Fabrikt dependency receive that exact version.

## Releases

The plugin and Fabrikt share a version number. To release, set `fabriktVersion` in `gradle.properties` to the Fabrikt version you are targeting, merge that, then create a GitHub release whose tag is that version. The publish workflow builds, signs, and uploads to Maven Central staging, then registers the deployment with the Central Publisher Portal. Promotion to Maven Central stays manual at <https://central.sonatype.com/publishing>.

## License

Licensed under the [Apache License 2.0](LICENSE).
