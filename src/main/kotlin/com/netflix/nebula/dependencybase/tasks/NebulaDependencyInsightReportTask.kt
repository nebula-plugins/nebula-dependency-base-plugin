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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.LegendRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.internal.graph.GraphRenderer
import java.util.*
import org.gradle.internal.logging.text.StyledTextOutput.Style.*


/**
 * Task extending {@link DependencyInsightReportTask} providing overridden report function to provide additional selection
 * reasons.
 */
open class NebulaDependencyInsightReportTask : DependencyInsightReportTask() {
    @Internal
    lateinit var reasonLookup: DependencyManagement

    /**
     * Copied verbatim from {@link DependencyInsightReportTask}, except for the marked changes.
     */
    @TaskAction
    override fun report() {
        val reportTasks = project.tasks.withType(DependencyInsightReportTask::class.java)
        val configuration = reportTasks.mapNotNull { it.configuration }.singleOrNull()
                ?: throw InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                        + "\nIt can be specified from the command line, e.g: '" + path + " --configuration someConf --dependency someDep'")

        val dependencySpec = reportTasks.mapNotNull { it.dependencySpec }.singleOrNull()
                ?: throw InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                        + "\nIt can be specified from the command line, e.g: '" + path + " --dependency someDep'")

        val output = textOutputFactory.create(javaClass)
        val renderer = GraphRenderer(output)

        val result = configuration.incoming.resolutionResult

        val selectedDependencies = LinkedHashSet<DependencyResult>()
        result.allDependencies { dependencyResult ->
            if (dependencySpec.isSatisfiedBy(dependencyResult)) {
                selectedDependencies.add(dependencyResult)
            }
        }

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in " + configuration.toString())
            return
        }

        val reporter = DependencyInsightReporter()
        val sortedDeps = try {
            reporter.prepare(selectedDependencies, versionSelectorScheme, versionComparator, versionParser)
        } catch (e: NoSuchMethodError) {
            reporter.legacyPrepare(selectedDependencies, versionSelectorScheme, versionComparator)
        }

        val nodeRenderer = NodeRenderer { target, node, alreadyRendered ->
            val leaf = node.children.isEmpty()
            target.text(if (leaf) configuration.name else node.name)
            if (alreadyRendered && !leaf) {
                target.withStyle(Info).text(" (*)")
            }
        }

        val legendRenderer = LegendRenderer(output)
        val dependencyGraphRenderer = DependencyGraphRenderer(renderer, nodeRenderer, legendRenderer)

        var i = 1
        for (dependency in sortedDeps) {
            renderer.visit({ out ->
                out.withStyle(Identifier).text(dependency.name)

                // Nebula enhancements start here
                val hasDescription = dependency.description != null && dependency.description.isNotEmpty()
                if (hasDescription) {
                    val reason = reasonLookup.getReason(configuration.name, calculateCoordinateFromId(dependency.id))
                    out.withStyle(Description).text(" ($reason)")
                }
                // Nebula enhancements end here

                when (dependency.resolutionState) {
                    RenderableDependency.ResolutionState.FAILED -> out.withStyle(Failure).text(" FAILED")
                    RenderableDependency.ResolutionState.UNRESOLVED -> out.withStyle(Failure).text(" (n)")
                    else -> {
                    }
                }
            }, true)
            dependencyGraphRenderer.render(dependency)
            val last = i++ == sortedDeps.size
            if (!last) {
                output.println()
            }

        }

        // Nebula enhancements start here
        val globalMessages = reasonLookup.getGlobalMessages()
        if (globalMessages.isNotEmpty()) {
            output.println()
            output.println(globalMessages)
        }
        // Nebula enhancements end here

        legendRenderer.printLegend()
    }

    fun calculateCoordinateFromId(id: Any): String {
        when (id) {
            is ModuleComponentIdentifier -> return "${id.group}:${id.module}"
        }
        return ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun DependencyInsightReporter.legacyPrepare(input: Collection<DependencyResult>, versionSelectorScheme: VersionSelectorScheme, versionComparator: VersionComparator): Collection<RenderableDependency> {
        return javaClass.getMethod("prepare", Collection::class.java, VersionSelectorScheme::class.java, VersionComparator::class.java)
                .invoke(this, input, versionSelectorScheme, versionComparator) as Collection<RenderableDependency>
    }
}
