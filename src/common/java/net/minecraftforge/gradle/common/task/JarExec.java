/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.task;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;

public class JarExec extends DefaultTask {
    private static final OutputStream NULL = new OutputStream() { @Override public void write(int b) throws IOException { } };

    protected final Property<String> tool;
    protected final ListProperty<String> args;
    protected final ConfigurableFileCollection classpath;
    protected boolean hasLog = true;

    private final Provider<File> toolFile;
    private final Provider<String> resolvedVersion;

    public JarExec() {
        tool = getProject().getObjects().property(String.class);
        toolFile = tool.map(toolStr -> MavenArtifactDownloader.gradle(getProject(), toolStr, false));
        resolvedVersion = tool.map(toolStr -> MavenArtifactDownloader.getVersion(getProject(), toolStr));

        args = getProject().getObjects().listProperty(String.class);
        classpath = getProject().getObjects().fileCollection();
    }

    @TaskAction
    public void apply() throws IOException {

        File jar = getToolJar().get();

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        File workDir = getProject().file("build/" + getName());
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        File logFile = new File(workDir, "log.txt");

        try (OutputStream log = hasLog ? new BufferedOutputStream(new FileOutputStream(logFile)) : NULL) {
            PrintWriter printer = new PrintWriter(log, true);
            getProject().javaexec(java -> {
                // Execute command
                java.setArgs(filterArgs(args.get()));
                printer.println("Args: " + java.getArgs().stream().map(m -> '"' + m +'"').collect(Collectors.joining(", ")));
                if (getClasspath() == null)
                    java.setClasspath(getProject().files(jar));
                else
                    java.setClasspath(getProject().files(jar, getClasspath()));
                java.getClasspath().forEach(f -> printer.println("Classpath: " + f.getAbsolutePath()));
                java.setWorkingDir(workDir);
                printer.println("WorkDir: " + workDir);
                java.setMain(mainClass);
                printer.println("Main: " + mainClass);
                printer.println("====================================");
                java.setStandardOutput(new OutputStream() {
                    @Override
                    public void flush() throws IOException {
                        log.flush();
                    }
                    @Override
                    public void close() {}
                    @Override
                    public void write(int b) throws IOException {
                        log.write(b);
                    }
                });
            }).rethrowFailure().assertNormalExitValue();
        }

        if (hasLog)
            postProcess(logFile);

        if (workDir.list().length == 0)
            workDir.delete();
    }

    protected List<String> filterArgs(List<String> args) {
        return args;
    }

    protected void postProcess(File log) {
    }

    public String getResolvedVersion() {
        return resolvedVersion.get();
    }

    @Input
    public boolean getHasLog() {
        return hasLog;
    }
    public void setHasLog(boolean value) {
        this.hasLog = value;
    }

    @InputFile
    public Provider<File> getToolJar() {
        return toolFile;
    }

    @Input
    public Property<String> getTool() {
        return tool;
    }

    @Input
    public ListProperty<String> getArgs() {
        return this.args;
    }

    @Optional
    @InputFiles
    public ConfigurableFileCollection getClasspath() {
        return this.classpath;
    }
}
