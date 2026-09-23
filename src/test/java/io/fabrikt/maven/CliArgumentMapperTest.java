package io.fabrikt.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliArgumentMapperTest {
    private final CliArgumentMapper mapper = new CliArgumentMapper();

    @TempDir
    Path projectDirectory;

    @Test
    void mapsScalarRepeatableAndBooleanArguments() throws Exception {
        DefaultPlexusConfiguration arguments = arguments("com.example.customer");
        arguments.addChild(value("serializationLibrary", "JACKSON_3"));
        DefaultPlexusConfiguration targets = new DefaultPlexusConfiguration("targets");
        targets.addChild(value("value", "http_models"));
        targets.addChild(value("value", "client"));
        arguments.addChild(targets);
        arguments.addChild(value("includeCompanionObject", "true"));
        arguments.addChild(value("disabledOption", "false"));

        List<String> result = mapper.map(
                projectDirectory,
                "src/main/openapi/customer.yaml",
                null,
                projectDirectory.resolve("target/generated-sources"),
                arguments);

        assertThat(result)
                .containsExactly(
                        "--output-directory",
                        projectDirectory.resolve("target/generated-sources").toString(),
                        "--api-file",
                        projectDirectory.resolve("src/main/openapi/customer.yaml").toString(),
                        "--base-package",
                        "com.example.customer",
                        "--serialization-library",
                        "JACKSON_3",
                        "--targets",
                        "http_models",
                        "--targets",
                        "client",
                        "--include-companion-object")
                .doesNotContain("--disabled-option");
    }

    @Test
    void acceptsKebabCaseArgumentNames() throws Exception {
        DefaultPlexusConfiguration arguments = new DefaultPlexusConfiguration("arguments");
        arguments.addChild(value("base-package", "com.example.customer"));
        arguments.addChild(value("serialization-library", "JACKSON_3"));

        List<String> result = mapper.map(
                projectDirectory, "customer.yaml", null, projectDirectory.resolve("generated"), arguments);

        assertThat(result)
                .containsSubsequence(
                        "--base-package",
                        "com.example.customer",
                        "--serialization-library",
                        "JACKSON_3");
    }

    @Test
    void keepsRemoteInputUrlsUnchanged() throws Exception {
        List<String> result = mapper.map(
                projectDirectory,
                "https://example.com/customer.yaml",
                null,
                projectDirectory.resolve("generated"),
                arguments("com.example.customer"));

        assertThat(result).containsSubsequence("--api-file", "https://example.com/customer.yaml");
    }

    @Test
    void requiresBasePackage() {
        assertThatThrownBy(() -> mapper.map(
                        projectDirectory,
                        "customer.yaml",
                        null,
                        projectDirectory.resolve("generated"),
                        new DefaultPlexusConfiguration("arguments")))
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("arguments.basePackage must be configured for every Fabrikt execution");
    }

    @Test
    void mapsNestedJsonSchemaInputAndRootName() throws Exception {
        DefaultPlexusConfiguration arguments = arguments("com.example.inventory");
        arguments.addChild(value("jsonSchemaRootName", "InventoryRecord"));

        List<String> result = mapper.map(
                projectDirectory,
                null,
                "src/main/schema/manifest.yaml#/spec/schemaObject",
                projectDirectory.resolve("generated"),
                arguments);

        assertThat(result).containsSubsequence(
                        "--json-schema-file",
                        projectDirectory.resolve("src/main/schema/manifest.yaml#/spec/schemaObject").toString(),
                        "--base-package",
                        "com.example.inventory",
                        "--json-schema-root-name",
                        "InventoryRecord")
                .doesNotContain("--api-file");
    }

    @Test
    void preservesJsonPointerSegmentsWhenResolvingLocalSchemaPath() throws Exception {
        List<String> result = mapper.map(
                projectDirectory,
                null,
                "manifest.yaml#/spec/../schemaObject",
                projectDirectory.resolve("generated"),
                arguments("com.example.inventory"));

        assertThat(result).containsSubsequence(
                "--json-schema-file", projectDirectory.resolve("manifest.yaml") + "#/spec/../schemaObject");
    }

    @Test
    void rejectsJsonSchemaRootNameWithoutJsonSchemaInput() {
        DefaultPlexusConfiguration arguments = arguments("com.example.inventory");
        arguments.addChild(value("jsonSchemaRootName", "InventoryRecord"));

        assertThatThrownBy(() -> mapper.map(
                        projectDirectory,
                        "customer.yaml",
                        null,
                        projectDirectory.resolve("generated"),
                        arguments))
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("arguments.jsonSchemaRootName requires jsonSchemaFile");
    }

    @Test
    void requiresExactlyOneInputFile() {
        assertThatThrownBy(() -> mapper.map(
                        projectDirectory,
                        null,
                        null,
                        projectDirectory.resolve("generated"),
                        arguments("com.example.inventory")))
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("Configure exactly one of inputFile or jsonSchemaFile for every Fabrikt execution");

        assertThatThrownBy(() -> mapper.map(
                        projectDirectory,
                        "customer.yaml",
                        "manifest.yaml",
                        projectDirectory.resolve("generated"),
                        arguments("com.example.inventory")))
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("Configure exactly one of inputFile or jsonSchemaFile for every Fabrikt execution");
    }

    @ParameterizedTest
    @ValueSource(strings = {"outputDirectory", "output-directory", "jsonSchemaFile", "json-schema-file"})
    void rejectsArgumentsManagedByThePlugin(String argumentName) {
        DefaultPlexusConfiguration arguments = arguments("com.example.customer");
        arguments.addChild(value(argumentName, "other"));

        assertThatThrownBy(() -> mapper.map(
                        projectDirectory,
                        "customer.yaml",
                        null,
                        projectDirectory.resolve("generated"),
                        arguments))
                .isInstanceOf(MojoFailureException.class)
                .hasMessage("arguments." + argumentName + " is managed by the Maven plugin and cannot be overridden");
    }

    private DefaultPlexusConfiguration arguments(String basePackage) {
        DefaultPlexusConfiguration arguments = new DefaultPlexusConfiguration("arguments");
        arguments.addChild(value("basePackage", basePackage));
        return arguments;
    }

    private DefaultPlexusConfiguration value(String name, String value) {
        DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration(name);
        configuration.setValue(value);
        return configuration;
    }
}
