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
import groovy.lang.Closure
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.execution.TaskExecutionGraph

class DependencyBasePlugin: Plugin<Project> {
    val dependencyManagement: DependencyManagement = DependencyManagement()
    lateinit var insightTask: NebulaDependencyInsightReportTask

    override fun apply(project: Project) {
        initializeDependencyBase(project)
        enableForceCollection(project)
        setupDependencyInsightEnhanced(project)
        project.gradle.taskGraph.whenReady( groovyClosure { taskGraph : TaskExecutionGraph ->
                if (!taskGraph.hasTask(insightTask)) {
                    dependencyManagement.disableMessageStore()
                }
            })
    }

    private fun initializeDependencyBase(project: Project) {
        project.extensions.extraProperties.set("nebulaDependencyBase", dependencyManagement)
    }

    private fun enableForceCollection(project: Project) {
        project.configurations.all { conf ->
            if (conf.state == Configuration.State.UNRESOLVED) {
                conf.incoming.beforeResolve {
                    val forced = conf.resolutionStrategy.forcedModules
                    forced.forEach { force -> dependencyManagement.addForce(conf.name, "${force.group}:${force.name}") }
                }
            }
        }
    }

    private fun setupDependencyInsightEnhanced(project: Project) {
        insightTask = project.tasks.replace("dependencyInsight", NebulaDependencyInsightReportTask::class.java)
        insightTask.reasonLookup = dependencyManagement
    }
}

inline fun <S,T> S.groovyClosure(crossinline call: (a: T) -> Unit) = object : Closure<Unit>(this) {
    @Suppress("unused")
    fun doCall(a: T) {
        call(a)
    }
}
