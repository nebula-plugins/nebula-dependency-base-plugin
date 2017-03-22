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

import com.netflix.nebula.dependencybase.internal.*

class DependencyManagement {
    val reasons: MutableList<Reason> = mutableListOf()
    val pluginMessages: MutableSet<String> = mutableSetOf()
    private var shouldStoreReason: Boolean = true

    fun addRecommendation(configuration: String, coordinate: String, version: String, source: String, plugin: String) {
        if (shouldStoreReason) reasons.add(Recommendation(configuration, coordinate, version, source))
    }

    fun addLock(configuration: String, coordinate: String, version: String, source: String, plugin: String) {
        if (shouldStoreReason) reasons.add(Lock(configuration, coordinate, version, source))
    }

    fun addForce(configuration: String, coordinate: String) {
        if (shouldStoreReason) reasons.add(Force(configuration, coordinate))
    }

    fun addReason(configuration: String, coordinate: String, message: String, plugin: String) {
        if (shouldStoreReason) reasons.add(DefaultReason(configuration, coordinate, message))
    }

    fun addPluginMessage(message: String) = if (shouldStoreReason) pluginMessages.add(message) else false

    fun getReason(configuration: String, coordinate: String): String {
        val recs = reasons.filter { it.configuration == configuration && it.coordinate == coordinate }.distinct().reversed()
        val reason = recs.joinToString(transform = Reason::getReason)

        return reason
    }

    fun getGlobalMessages(): String = pluginMessages.joinToString(separator = System.lineSeparator())

    fun disableMessageStore() {
        shouldStoreReason = false
        reasons.clear()
        pluginMessages.clear()
    }

    fun enableMessageStore() {
        shouldStoreReason = true
    }
}
