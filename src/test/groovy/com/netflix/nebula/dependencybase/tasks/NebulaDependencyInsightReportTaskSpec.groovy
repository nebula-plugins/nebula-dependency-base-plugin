package com.netflix.nebula.dependencybase.tasks

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.BuildResult

class NebulaDependencyInsightReportTaskSpec extends IntegrationTestKitSpec {
    def "generate a dependencyInsight report with more than just chosen by rule"() {
        def graph = new DependencyGraphBuilder().addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:foo:1.1.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()


        buildFile << """\
            plugins {
                id "java"
                id "nebula.dependency-base"
            }
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            configurations.all {
                resolutionStrategy {
                    force "test.nebula:foo:1.0.0"
                }
            }
            
            dependencies {
                implementation "test.nebula:foo:1.+"
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        result.output.contains "test.nebula:foo:1.0.0 (forced)"
    }
}