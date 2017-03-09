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

import com.github.zafarkhaja.semver.Version
import com.netflix.nebula.dependencybase.internal.Recommendation

class DependencyManagement {
    val recommendations : MutableMap<String, MutableSet<Recommendation>> = mutableMapOf()
    val forces : MutableSet<String> = mutableSetOf()

    fun addRecommendation(coordinate: String, version: String, source: String) {
        val myset = recommendations.getOrPut(coordinate) { mutableSetOf<Recommendation>() }
        myset.add(Recommendation(coordinate, Version.valueOf(version), source))
    }

    fun forced(coordinate: String) = forces.add(coordinate)

    fun forced(group: String, name: String) = forced(groupNameToCoordinate(group, name))

    fun getRecommendedVersion(coordinate: String): String = recommendations[coordinate]?.max()?.version?.toString() ?: ""

    fun getRecommendedVersion(group: String, name: String): String = getRecommendedVersion(groupNameToCoordinate(group, name))

    fun getReason(coordinate: String): String {
        val reason = StringBuilder()
        var shouldAddIgnore = false

        if (forces.contains(coordinate)) {
            reason.append("forced")
            shouldAddIgnore = true
        }

        val recs = recommendations[coordinate]
        val max = recs?.max() ?: return reason.toString()
        val recsLessMax = recs.minus(max)
        val ignores = if (recsLessMax.isNotEmpty()) recsLessMax.joinToString(prefix = " skip ", transform = { it.source }) else ""
        if (shouldAddIgnore) reason.append(", ignore ")
        reason.append("recommend: ${max.version} via ${max.source}$ignores")

        return reason.toString()
    }

    private fun groupNameToCoordinate(group: String, name: String): String = "$group:$name"
}