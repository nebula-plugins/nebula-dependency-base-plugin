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

import com.netflix.nebula.dependencybase.tasks.NebulaDependencyInsightReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionSelector

class DependencyBasePlugin : Plugin<Project> {
    private val dependencyManagement : DependencyManagement = DependencyManagement()

    override fun apply(project: Project) {
        initializeDependencyBase(project)
        manipulateDependencies(project)
//        setupDependenciesEnhanced(project)
        setupDependencyInsightEnhanced(project)
    }

    private fun initializeDependencyBase(project: Project) {
        project.extensions.extraProperties.set("nebulaDependencyBase", dependencyManagement)
    }

    private fun manipulateDependencies(project: Project) {
        project.configurations.all { conf ->
            if (conf.state == Configuration.State.UNRESOLVED) {
                conf.incoming.beforeResolve {
                    val forced = conf.resolutionStrategy.forcedModules
                    forced.forEach { force -> dependencyManagement.forced(force.group, force.name) }

                    conf.resolutionStrategy.eachDependency { details ->
                        val requested = details.requested
                        if (dependencyNeedsRecommendation(forced, requested)) {
                            val recommendedVersion = dependencyManagement.getRecommendedVersion(requested.group, requested.name)
                            if (recommendedVersion.isNotBlank()) {
                                details.useVersion(recommendedVersion)
                                project.logger.info("recommending version $recommendedVersion for dependency ${requested.group}:${requested.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dependencyNeedsRecommendation(forced: MutableSet<ModuleVersionSelector>, requested: ModuleVersionSelector): Boolean = versionMissing(requested) && dependencyNotForced(forced, requested)

    private fun versionMissing(requested: ModuleVersionSelector): Boolean = requested.version.isNullOrBlank()

    private fun dependencyNotForced(forced: MutableSet<ModuleVersionSelector>, requested: ModuleVersionSelector): Boolean = forced.find { force -> force.group == requested.group && force.name == requested.name } == null


//    private fun setupDependenciesEnhanced(project: Project) {
//        project.plugins.apply(ProjectReportsPlugin::class.java)
//        val dependenciesTask = project.tasks.findByName("dependencies")
//        val dependencyReportTask : DependencyReportTask = project.tasks.findByName("dependencyReport") as DependencyReportTask
//        val dependenciesEnhancedTask = project.tasks.create("dependenciesEnhanced", NebulaDependencyReportTask::class.java)
//        dependenciesEnhancedTask.dependsOn(dependencyReportTask)
//        dependenciesEnhancedTask.dependencyReportFile = dependencyReportTask.outputFile
//        dependenciesTask.finalizedBy(dependenciesEnhancedTask)
//    }

    private fun setupDependencyInsightEnhanced(project: Project) {
        val depInsightEnhancedTask = project.tasks.create("dependencyInsightEnhanced", NebulaDependencyInsightReportTask::class.java)
        depInsightEnhancedTask.reasonLookup = dependencyManagement
    }
}