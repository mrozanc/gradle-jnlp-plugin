/*
 * Copyright 2014 the original author or authors.
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

import groovy.transform.PackageScope
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails

import javax.inject.Inject

class GradleJnlpWarPluginExtension {

    /* should allow following dsl:
    apply plugin: 'de.gliderpilot.jnlp-war'
    jnlp {
        versions {
            v1 'org.example:application:1.0:webstart@zip'
            v2 'org.example:application:2.0:webstart@zip'
            v3 'org.example:application:3.0:webstart@zip'
        }
        launchers {
            v2 {
                rename 'launch.jnlp', 'launch_v2.jnlp'
                jardiff {
                    from v1
                }
            }
            v3 {
                jardiff {
                    from v1, v2
                }
            }
        }
    }

    */

    private GradleJnlpWarPlugin plugin
    private Project project

    final CopySpec launchers

    private Map

    @Inject
    GradleJnlpWarPluginExtension(GradleJnlpWarPlugin plugin, Project project) {
        this.plugin = plugin
        this.project = project
        launchers = project.copySpec { CopySpec copySpec ->
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            includeEmptyDirs = false
        }
    }

    Project project(String path) {
        project.project(path)
    }

    void from(Project jnlpProject) {
        def webstartZipTask = jnlpProject.tasks.getByName('webstartDistZip')
        versions {
            "${jnlpProject.version}" project.dependencies.project(path: jnlpProject.path, configuration: 'webstartZip')
        }
        launchers {
            "${jnlpProject.version}"()
        }
        project.war.dependsOn webstartZipTask
    }

    void versions(Closure closure) {
        closure.delegate = new Versions(project)
        closure()
    }

    void launchers(Closure closure) {
        closure.delegate = new Launchers(project)
        closure()
    }

    private class Versions {
        private Project project

        Versions(Project project) {
            this.project = project
        }

        @Override
        Object invokeMethod(String name, Object args) {
            project.configurations.maybeCreate(name)
            return project.dependencies.invokeMethod(name, args)
        }
    }

    private class Launchers {

        private Project project

        Launchers(Project project) {
            this.project = project
        }

        @Override
        Object invokeMethod(String name, Object args) {
            Configuration configuration = project.configurations.getByName(name)
            def zipTree = project.zipTree(configuration.singleFile)

            Launcher launcher = new Launcher()

            CopySpec jnlpFileSpec = project.copySpec {
                from zipTree
                include '**/*.jnlp'
                filter launcher.filterJnlpFiles
            }
            CopySpec nonJnlpFileSpec = project.copySpec {
                from zipTree
                exclude '**/*.jnlp'
            }
            if (args) {
                Closure closure = args[0]
                closure.delegate = launcher
                closure.resolveStrategy = Closure.DELEGATE_ONLY
                closure()
            }
            return launchers.with(jnlpFileSpec, nonJnlpFileSpec)
        }
    }

    private class Launcher {

        String oldJnlpFileName
        String newJnlpFileName

        def filterJnlpFiles = { line ->
            if (oldJnlpFileName && newJnlpFileName)
                line = line.replace(oldJnlpFileName, newJnlpFileName)
            line.contains('jnlp.versionEnabled') || line.contains('jnlp.packEnabled') ? '' : line
        }

        @Override
        Object invokeMethod(String name, Object args) {
            if (name == 'rename') {
                oldJnlpFileName = args[0]
                newJnlpFileName = args[1]
                return null
            }
            return super.invokeMethod(name, args)
        }
    }

}