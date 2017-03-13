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

import spock.lang.Specification

class DependencyManagementSpec extends Specification {
    def "request for non existent coordinate returns empty string"() {
        given:
        def management = new DependencyManagement()

        expect:
        management.getReason("compile", "fake:dep") == ""
    }

    def "able to add a recommendation and retrieve reason"() {
        given:
        def management = new DependencyManagement()
        final coordinate = "test.nebula:foo"

        when:
        management.addRecommendation("compile", coordinate, "1.0.0", "TestRecommender", "test")

        then:
        management.reasons.size() == 1
        management.getReason("compile", coordinate) == "recommend 1.0.0 via TestRecommender"
    }

    def "handle non semver versions"() {
        given:
        def management = new DependencyManagement()
        final coordinate = "test.nebula:foo"

        when:
        management.addRecommendation("compile", coordinate, "1.2", "OtherRecommender", "test")
        management.addRecommendation("compile", coordinate, "1.0", "TestRecommender", "test")

        then:
        management.reasons.size() == 2
        management.getReason("compile", coordinate) == "recommend 1.0 via TestRecommender, recommend 1.2 via OtherRecommender"
    }

    def "force"() {
        given:
        def management = new DependencyManagement()
        final coordinate = "test.nebula:foo"

        when:
        management.addForce("compile", "test.nebula:foo")
        management.addRecommendation("compile", coordinate, "1.2.0", "OtherRecommender", "test")

        then:
        management.getReason("compile", coordinate) == "recommend 1.2.0 via OtherRecommender, forced"
    }
}
