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

import com.netflix.nebula.dependencybase.DependencyManagement
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationConverter
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.internal.graph.GraphRenderer
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import javax.inject.Inject

/**
 * Mostly a copy of DependencyInsightReportTask from gradle/gradle which is under a Apache 2.0 license. Modified to Kotlin.
 * Modified to provide more information on why a dependenecy version was selected.
 */
open class NebulaDependencyInsightReportTask : DefaultTask() {
    var reasonLookup: DependencyManagement? = null

    /**
     * Configuration to look the dependency in
     */
    var configuration: Configuration? = null

    /**
     * Selects the dependency (or dependencies if multiple matches found) to show the report for.
     */
    var dependencySpec: Spec<DependencyResult>? = null

    @Inject
    open fun getTextOutputFactory(): StyledTextOutputFactory {
        throw UnsupportedOperationException()
    }

    @Inject
    open fun getVersionSelectorScheme(): VersionSelectorScheme {
        throw UnsupportedOperationException()
    }

    @Inject
    open fun getVersionComparator(): VersionComparator {
        throw UnsupportedOperationException();
    }

    /**
     * Configures the dependency to show the report for.
     * Multiple notation formats are supported: Strings, instances of {@link Spec}
     * and groovy closures. Spec and closure receive {@link DependencyResult} as parameter.
     * Examples of String notation: 'org.slf4j:slf4j-api', 'slf4j-api', or simply: 'slf4j'.
     * The input may potentially match multiple dependencies.
     * See also {@link DependencyInsightReportTask#setDependencySpec(Spec)}
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --dependency slf4j</pre>
     *
     * @param dependencyInsightNotation
     */
    @Option(option = "dependency", description = "Shows the details of given dependency.")
    fun setDependencySpec(dependencyInsightNotation: Any) {
        val parser = DependencyResultSpecNotationConverter.parser()
        this.dependencySpec = parser.parseNotation(dependencyInsightNotation)
    }

    /**
     * Sets the configuration (via name) to look the dependency in.
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --configuration runtime --dependency slf4j</pre>
     *
     * @param configurationName
     */
    @Option(option = "configuration", description = "Looks for the dependency in given configuration.")
    fun setConfiguration(configurationName: String) {
        this.configuration = project.configurations.getByName(configurationName)
    }

    @TaskAction
    fun report() {
        val conf = configuration ?:
            throw InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                    + "\nIt can be specified from the command line, e.g: '$path --configuration someConf --dependency someDep'")

        val depSpec = dependencySpec ?:
            throw InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: '$path --dependency someDep'")

        val output: StyledTextOutput = getTextOutputFactory().create(javaClass)
        val renderer: GraphRenderer = GraphRenderer(output)

        val result: ResolutionResult = conf.incoming.resolutionResult;

        val selectedDependencies: MutableSet<DependencyResult> = mutableSetOf()
        result.allDependencies {
            if (depSpec.isSatisfiedBy(it)) {
                selectedDependencies.add(it)
            }
        }

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in ${conf}")
            return
        }

        val sortedDeps = DependencyInsightReporter().prepare(selectedDependencies, getVersionSelectorScheme(), getVersionComparator())

        val nodeRenderer = NodeRenderer { target, node, alreadyRendered ->
            val leaf = node.children.isEmpty()
            target.text(if (leaf) conf.name else node.name)
            if (alreadyRendered && !leaf) {
                target.withStyle(StyledTextOutput.Style.Info).text(" (*)")
            }
        }

        val dependencyGraphRenderer = DependencyGraphRenderer(renderer, nodeRenderer)

        var i = 1
        for (dependency in sortedDeps) {
            renderer.visit( { out ->
                out.withStyle(StyledTextOutput.Style.Identifier).text(dependency.name);
                if (dependency.description?.isNotEmpty()?:false) {
                    out.withStyle(StyledTextOutput.Style.Description).text(" (${reasonLookup?.getReason(conf.name, calculateCoordinateFromId(dependency.id))})")
                }
                if (!dependency.isResolvable) {
                    out.withStyle(StyledTextOutput.Style.Failure).text(" FAILED")
                }
            }, true);
            dependencyGraphRenderer.render(dependency)
            val last = i++ == sortedDeps.size
            if (!last) {
                output.println()
            }
        }

        output.println()
        output.println(reasonLookup?.getGlobalMessages())
        dependencyGraphRenderer.printLegend()
    }

    fun calculateCoordinateFromId(id: Any): String {
        when (id) {
            is ModuleComponentIdentifier -> return "${id.group}:${id.module}"
        }
        return ""
    }
}
