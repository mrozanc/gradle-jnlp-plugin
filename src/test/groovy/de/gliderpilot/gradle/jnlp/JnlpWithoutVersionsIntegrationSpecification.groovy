package de.gliderpilot.gradle.jnlp

import spock.lang.Shared

class JnlpWithoutVersionsIntegrationSpecification extends AbstractPluginSpecification {

    @Shared
    def jnlp

    def setupSpec() {
        IntegrationTestProject.enhance(project())
        project.buildFile << """\
            apply plugin: 'groovy'
            apply plugin: 'application'
            apply plugin: 'de.gliderpilot.jnlp'

            buildscript {
                dependencies {
                    classpath files('${new File('build/classes/main').absolutePath}')
                    classpath files('${new File('build/resources/main').absolutePath}')
                }
            }

            jnlp {
                useVersions = false
            }

            version = '1.0'

            repositories {
                jcenter()
            }
            dependencies {
                compile 'org.codehaus.groovy:groovy-all:2.3.1'
            }
            mainClassName = 'de.gliderpilot.jnlp.test.Main'
        """.stripIndent()

        project.settingsFile << """\
            rootProject.name = 'test'
        """.stripIndent()
        project.file('src/main/groovy/de/gliderpilot/jnlp/test').mkdirs()
        project.file('src/main/groovy/de/gliderpilot/jnlp/test/Main.groovy') << """\
            package de.gliderpilot.jnlp.test
            class Main {
                static main(args) {
                    println "test"
                }
            }
        """.stripIndent()
        project.run ':generateJnlp', ':copyJars', ':generateVersionXml'
        def jnlpFile = project.file('build/tmp/jnlp/launch.jnlp')
        jnlp = new XmlSlurper().parse(jnlpFile)
    }

    def cleanupSpec() {
        project.copy {
            from project.file("build/tmp/jnlp/")
            into '/tmp/'
        }
    }

    def 'jars entry is not empty'() {
        when:
        def jars = jnlp.resources.jar

        then:
        !jars.isEmpty()
    }

    def 'attributes for #artifact are correct'() {
        when:
        def jar = jnlp.resources.jar.find { it.@href =~ /$artifact/ }

        then:
        jar != null

        and:
        jar.@href.text() == "lib/${artifact}.jar"

        and:
        jar.@main.text() == main

        where:
        artifact             | main
        'groovy-all__V2.3.1' | ""
        'test__V1.0'         | "true"
    }
}
