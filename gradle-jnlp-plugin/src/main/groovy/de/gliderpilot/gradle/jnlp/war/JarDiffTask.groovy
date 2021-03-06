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

import de.gliderpilot.gradle.jnlp.JavaHomeAware
import jnlp.sample.jardiff.JarDiff
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class JarDiffTask extends DefaultTask {

    @InputFiles
    FileCollection oldVersions

    @InputFiles
    FileCollection newVersion

    @OutputDirectory
    File into

    void oldVersion(Configuration configuration) {
        if (oldVersions == null)
            oldVersions = configuration
        else
            oldVersions += configuration
    }

    void newVersion(Configuration configuration) {
        newVersion = configuration
    }

    void into(into) {
        this.into = project.file(into)
    }

    @TaskAction
    void createJardiffs() {
        project.delete(into.listFiles())
        def toMap = {
            [file: it, baseName: it.name - ~/__V.*$/, version: it.name.replaceAll(/.*?__V(.*?)\.jar.*/, '$1')]
        }
        def onlyVersionedJars = { it.name ==~ /.*__V.*\.jar(?:\.pack(?:\.gz)?)?/ }
        def oldFiles = oldVersions.collect {
            project.zipTree(it).filter(onlyVersionedJars).files
        }.flatten().collect(toMap)
        def newFiles = project.zipTree(newVersion.singleFile)
                .filter(onlyVersionedJars)
                .collect(toMap)
                .inject([:]) { map, file ->
            map[file.baseName] = file
            map
        }
        oldFiles.each { oldFile ->
            def newFile = newFiles[oldFile.baseName]
            if (newFile && newFile.version != oldFile.version)
                createJarDiff(oldFile, newFile)
        }
    }

    def createJarDiff(oldVersion, newVersion) {
        File oldFile = getJar(oldVersion.file)
        File newFile = getJar(newVersion.file)
        File diffJar = new File(into, "${oldVersion.baseName}__V${oldVersion.version}__V${newVersion.version}.diff.jar")
        logger.info("creating jardiff $diffJar.name")
        try {
            diffJar.withOutputStream { os ->
                JarDiff.createPatch(oldFile.canonicalPath, newFile.canonicalPath, os, true)
            }
        } catch (e) {
            logger.warn("failed to create $diffJar.name")
            project.delete(diffJar)
            return
        }
        if (newVersion.file.name.endsWith('.pack.gz')) {
            // new file is in pack200 format, therefore do also pack the jardiff
            File diffJarPacked = new File("${diffJar}.pack.gz")
            logger.info("packing $diffJar.name with pack200")
            try {
                // do the repack on a tmp file, because this might go wrong and we are stuck with a scrambled file
                File tmpJar = tmpFile(diffJar)
                // first repack
                JavaHomeAware.exec(project, "pack200", "--repack", tmpJar)
                // because the repacking might have altered the class files, resulting in a security exception
                // when packing again
                JavaHomeAware.exec(project, "pack200", diffJarPacked, tmpJar)
            } catch (e) {
                // there was most probably a security exceptionls
                // the file may be created and have size 0 -- delete
                project.delete(diffJarPacked)
                logger.info("failed to pack $diffJar.name using default params -- retrying with param --effort=0")
                // use the unaltered diffJar as source, using --effort=0 is save, because it does not alter the class files
                JavaHomeAware.exec(project, "pack200", "--effort=0", diffJarPacked, diffJar)
            }
            project.delete(diffJar)
            if (diffJarPacked.size() >= newVersion.file.size()) {
                // only keep jardiff.pack.gz if smaller than the full file
                logger.info("$diffJarPacked.name is not smaller than $newVersion.file.name")
                project.delete(diffJarPacked)
            }
            return
        }
        if (diffJar.size() >= newVersion.file.size()) {
            // only keep jardiff if smaller than the full file
            logger.info("$diffJar.name is not smaller than $newVersion.file.name")
            project.delete(diffJar)
        }
    }

    File getJar(File file) {
        if (file.name.endsWith('.jar'))
            return file
        File jar = new File(into.parentFile, file.name - '.pack.gz')
        JavaHomeAware.exec(project, "unpack200", file, jar)
        return jar
    }

    File tmpFile(File file) {
        project.copy {
            from file
            into into.parentFile
        }
        return new File(into.parentFile, file.name)
    }
}
