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
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.LegendRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import java.util.*

/**
 * Mostly a copy of DependencyInsightReportTask from gradle/gradle which is under a Apache 2.0 license. Modified to Kotlin.
 * Modified to provide more information on why a dependenecy version was selected.
 */
open class NebulaDependencyInsightReportTask : DependencyInsightReportTask() {
    var reasonLookup: DependencyManagement? = null

    @TaskAction
    override fun report() {
        val configuration = getConfiguration() ?: throw InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                + "\nIt can be specified from the command line, e.g: '" + path + " --configuration someConf --dependency someDep'")

        if (dependencySpec == null) {
            throw InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: '" + path + " --dependency someDep'")
        }


        val output = textOutputFactory.create(javaClass)
        val renderer = GraphRenderer(output)

        val result = configuration!!.getIncoming().getResolutionResult()

        val selectedDependencies = LinkedHashSet<DependencyResult>()
        result.allDependencies(Action<DependencyResult> { dependencyResult ->
            if (dependencySpec.isSatisfiedBy(dependencyResult)) {
                selectedDependencies.add(dependencyResult)
            }
        })

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in " + configuration.toString())
            return
        }

        val sortedDeps = DependencyInsightReporter().prepare(selectedDependencies, getVersionSelectorScheme(), getVersionComparator())

        val nodeRenderer = NodeRenderer { target, node, alreadyRendered ->
            val leaf = node.children.isEmpty()
            target.text(if (leaf) configuration!!.getName() else node.name)
            if (alreadyRendered && !leaf) {
                target.withStyle(StyledTextOutput.Style.Info).text(" (*)")
            }
        }

        val legendRenderer = LegendRenderer(output)
        val dependencyGraphRenderer = DependencyGraphRenderer(renderer, nodeRenderer, legendRenderer)

        var i = 1
        for (dependency in sortedDeps) {
            renderer.visit({ out ->
                out.withStyle(StyledTextOutput.Style.Identifier).text(dependency.name)

                if (dependency.description?.isNotEmpty()?:false) {
                    out.withStyle(StyledTextOutput.Style.Description).text(" (${reasonLookup?.getReason(configuration.name, calculateCoordinateFromId(dependency.id))})")
                }

                when (dependency.resolutionState) {
                    RenderableDependency.ResolutionState.FAILED -> out.withStyle(StyledTextOutput.Style.Failure).text(" FAILED")
                    RenderableDependency.ResolutionState.RESOLVED -> {
                    }
                    RenderableDependency.ResolutionState.UNRESOLVED -> out.withStyle(StyledTextOutput.Style.Failure).text(" (n)")
                }
            }, true)
            dependencyGraphRenderer.render(dependency)
            val last = i++ == sortedDeps.size
            if (!last) {
                output.println()
            }

        }

        output.println()
        output.println(reasonLookup?.getGlobalMessages())
        legendRenderer.printLegend()
    }

    fun calculateCoordinateFromId(id: Any): String {
        when (id) {
            is ModuleComponentIdentifier -> return "${id.group}:${id.module}"
        }
        return ""
    }
}
