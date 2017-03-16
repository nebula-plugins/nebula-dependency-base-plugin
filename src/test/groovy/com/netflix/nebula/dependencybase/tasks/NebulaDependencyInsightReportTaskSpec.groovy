/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.dependencybase.tasks

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class NebulaDependencyInsightReportTaskSpec extends IntegrationSpec {
    def "generate a dependencyInsight report with more than just chosen by rule"() {
        def graph = new DependencyGraphBuilder().addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:foo:1.1.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()


        buildFile << """\
            plugins {
                id "java"
            }
            apply plugin: 'nebula.dependency-base'
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            configurations.all {
                resolutionStrategy {
                    force "test.nebula:foo:1.0.0"
                }
            }
            
            dependencies {
                compile "test.nebula:foo:1.+"
            }
            """.stripIndent()

        when:
        def result = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        result.standardOutput.contains "test.nebula:foo:1.0.0 (forced)"
    }

    def "display global info message"() {
        def graph = new DependencyGraphBuilder().addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:foo:1.1.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()


        buildFile << """\
            plugins {
                id "java"
            }
            apply plugin: 'nebula.dependency-base'
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            project.nebulaDependencyBase.addPluginMessage("test plugin message")
            project.nebulaDependencyBase.addPluginMessage("and another")
            
            dependencies {
                compile "test.nebula:foo:1.+"
            }
            """.stripIndent()

        when:
        def result = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        result.standardOutput.contains "test plugin message${System.lineSeparator()}and another"
    }
}
