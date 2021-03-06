/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gliderpilot.gradle.jnlp.war

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.WarPlugin

/**
 * This is the main plugin file. Put a description of your plugin here.
 */
class GradleJnlpWarPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.apply plugin: 'war'
        project.extensions.create('jnlpWar', GradleJnlpWarPluginExtension, project)
        project.dependencies {
            getClass().getResourceAsStream("/META-INF/gradle-plugins/de.gliderpilot.jnlp-war.properties")?.withStream { is ->
                Properties properties = new Properties()
                properties.load(is)
                String servletVersion = properties."implementation-version"
                runtime "de.gliderpilot.gradle.jnlp:jnlp-servlet:${servletVersion}"
            }
        }
    }
}
