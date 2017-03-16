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
package com.netflix.nebula.dependencybase

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class RecommendationDependencyBasePluginIntegrationSpec extends IntegrationSpec {
    def "recommend versions of dependencies are explained in dependencyInsightEnhanced"() {
        given:
        setup1Dependency()

        when:
        def results = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        println results?.standardError
        println results?.standardOutput
        results.standardOutput.contains "test.nebula:foo:1.0.0 (recommend 1.0.0 via NebulaTest)"
    }

    def "forces reported"() {
        given:
        setup1DependencyForce()

        when:
        def results = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        results.standardOutput.contains "test.nebula:foo:1.0.0 (forced, recommend 2.0.0 via NebulaTest)"
    }

    def "multiproject sees recommendations"() {
        given:
        setupMultiproject()

        when:
        def onefoo = runTasks(":one:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        onefoo.standardOutput.contains "test.nebula:foo:1.0.0 (recommend 1.0.0 via NebulaTest)"

        when:
        def twofoo = runTasks(":two:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        twofoo.standardOutput.contains "test.nebula:foo:1.0.0 (recommend 1.0.0 via NebulaTest)"

        when:
        def twobar = runTasks(":two:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "bar")

        then:
        twobar.standardOutput.contains "test.nebula:bar:2.0.0 (recommend 2.0.0 via NebulaTest)"
    }

    def "detect complete substitute foo to bar and give insight"() {
        def graph = new DependencyGraphBuilder()
                .addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:bar:2.0.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()
        buildFile << """\
            plugins {
                id "java"
            }
            
            apply plugin: "nebula.dependency-base"
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            configurations.all {
                resolutionStrategy {
                    eachDependency { details ->
                        if (details.requested.group == "test.nebula" && details.requested.name == "foo") {
                            details.useTarget "test.nebula:bar:2.0.0"
                        }
                    }
                }
            }

            project.nebulaDependencyBase.addReason("compileClasspath", "test.nebula:bar", "possible replacement of test.nebula:foo", "test")

            dependencies {
                compile "test.nebula:foo:1.0.0"
            }
            """.stripIndent()

        when:
        def results = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        results.standardOutput.contains "test.nebula:bar:2.0.0 (possible replacement of test.nebula:foo)"
    }

    def setup1Dependency() {
        def graph = new DependencyGraphBuilder().addModule("test.nebula:foo:1.0.0").build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id "java"
            }
            
            apply plugin: "nebula.dependency-base"
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            configurations.all {
                resolutionStrategy {
                    eachDependency { details ->
                        if (details.requested.group == "test.nebula" && details.requested.name == "foo") {
                            details.useVersion "1.0.0"
                        }
                    }
                }
            }
            
            project.nebulaDependencyBase.addRecommendation("compileClasspath", "test.nebula:foo", "1.0.0", "NebulaTest", "test")
            
            dependencies {
                compile "test.nebula:foo"
            }
            """.stripIndent()
    }

    def setup1DependencyForce() {
        def graph = new DependencyGraphBuilder()
                .addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:foo:2.0.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id "java"
            }
            apply plugin: "nebula.dependency-base"
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            project.nebulaDependencyBase.addRecommendation("compileClasspath", "test.nebula:foo", "2.0.0", "NebulaTest", "test")

            configurations.all {
                resolutionStrategy {
                    force "test.nebula:foo:1.0.0"
                }
            }
            
            dependencies {
                compile "test.nebula:foo"
            }
            """.stripIndent()
    }

    def setupMultiproject() {
        def graph = new DependencyGraphBuilder()
                .addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:bar:2.0.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            apply plugin: "nebula.dependency-base"
            
            subprojects {
                apply plugin: "nebula.dependency-base"
                apply plugin: "java"
                
                configurations.all {
                    resolutionStrategy {
                        eachDependency { details ->
                            if (details.requested.group == "test.nebula" && details.requested.name == "foo") {
                                details.useVersion "1.0.0"
                            }
                            if (details.requested.group == "test.nebula" && details.requested.name == "bar") {
                                details.useVersion "2.0.0"
                            }
                        }
                    }
                }
                
                project.nebulaDependencyBase.addRecommendation("compileClasspath", "test.nebula:foo", "1.0.0", "NebulaTest", "test")
                project.nebulaDependencyBase.addRecommendation("compileClasspath", "test.nebula:bar", "2.0.0", "NebulaTest", "test")
                
                repositories {
                    ${generator.mavenRepositoryBlock}
                }
            }
            """.stripIndent()

        addSubproject("one", """\
            dependencies {
                compile "test.nebula:foo"
            }
            """.stripIndent())
        addSubproject("two", """\
            dependencies {
                compile project(":one")
                compile "test.nebula:bar"
            }
            """.stripIndent())
    }
}
