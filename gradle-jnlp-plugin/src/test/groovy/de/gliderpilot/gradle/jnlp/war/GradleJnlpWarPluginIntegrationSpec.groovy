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

import nebula.test.IntegrationSpec
import spock.lang.Ignore

class GradleJnlpWarPluginIntegrationSpec extends IntegrationSpec {

    File warBuildFile

    def setup() {
        fork = true
        writeHelloWorld('de.gliderpilot.gradle.jnlp.test')
        buildFile << '''\
            apply plugin: 'application'
            apply plugin: 'de.gliderpilot.jnlp'
            apply plugin: 'maven-publish'
            group = 'de.gliderpilot.gradle.jnlp.test'
            mainClassName = 'de.gliderpilot.gradle.jnlp.test.HelloWorld'
            publishing {
                repositories {
                    maven {
                        url "$buildDir/repo"
                    }
                }
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        artifact webstartDistZip {
                            classifier "webstart"
                        }
                    }
                }
            }
            jnlp {
                signJarParams = [alias: 'myalias', storepass: 'mystorepass',
                    keystore: 'file:keystore.ks']
            }
            if (!file('keystore.ks').exists())
                ant.genkey(alias: 'myalias', storepass: 'mystorepass', dname: 'CN=Ant Group, OU=Jakarta Division, O=Apache.org, C=US',
                           keystore: 'keystore.ks')
        '''.stripIndent()
        warBuildFile = new File(addSubproject('war'), 'build.gradle')
        warBuildFile << '''\
            apply plugin: 'de.gliderpilot.jnlp-war'
            repositories {
                maven {
                    url "$rootDir/build/repo"
                }
            }
            jnlpWar {
                from rootProject
            }
            task unzipWar(type: Sync) {
                from zipTree(war.outputs.files.singleFile)
                into "build/tmp/warContent"
            }
            war.finalizedBy unzipWar
        '''.stripIndent()
        setVersion("1.0")
    }

    def setVersion(String version) {
        file('gradle.properties').text = """\
            version=$version
        """.stripIndent()
    }

    def "war contains webstart files from project"() {
        expect:
        runTasksSuccessfully("build")
        fileExists("war/build/libs/war-1.0.war")
        fileExists("war/build/tmp/warContent/launch.jnlp")
        fileExists("war/build/tmp/warContent/lib/${moduleName}__V1.0.jar.pack.gz")
    }

    @Ignore("not yet ready")
    def "launcher for project can be further refined"() {
        setup:
        runTasksSuccessfully(':publish')
        setVersion("1.1")

        warBuildFile << '''
            jnlpWar {
                versions {
                    "1.0" "${rootProject.group}:${rootProject.name}:1.0:webstart@zip"
                }
                launchers {
                    "1.0" {
                        rename "launch.jnlp", "launch-1.0.jnlp"
                    }
                    "1.1" {
                        rename "launch.jnlp", "launch-1.1.jnlp"
                    }
                }
            }
        '''.stripIndent()

        expect:
        runTasksSuccessfully("build")
        fileExists("war/build/libs/war-1.1.war")
        fileExists("war/build/tmp/warContent/launch.jnlp")
        fileExists("war/build/tmp/warContent/lib/${moduleName}__V1.0.jar.pack.gz")
    }


}