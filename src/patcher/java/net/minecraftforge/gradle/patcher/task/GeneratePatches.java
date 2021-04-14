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

package net.minecraftforge.gradle.patcher.task;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.nio.file.Path;

public class GeneratePatches extends DefaultTask {

    private final RegularFileProperty base;
    private final RegularFileProperty modified;
    private final DirectoryProperty output;
    private final Property<ArchiveFormat> outputFormat;
    private final Property<String> originalPrefix;
    private final Property<String> modifiedPrefix;
    private int contextLines = -1;
    private boolean autoHeader;
    private boolean verbose;
    private boolean printSummary;

    public GeneratePatches() {
        ObjectFactory objects = getProject().getObjects();

        base = objects.fileProperty();
        modified = objects.fileProperty();
        output = objects.directoryProperty();
        outputFormat = objects.property(ArchiveFormat.class);
        originalPrefix = objects.property(String.class)
                .convention("a/");
        modifiedPrefix = objects.property(String.class)
                .convention("b/");
    }

    @TaskAction
    public void doTask() throws Exception {
        Path basePath = base.get().getAsFile().toPath();
        Path modifiedPath = modified.get().getAsFile().toPath();
        Path outputPath = output.get().getAsFile().toPath();
        getProject().getLogger().info("Base: {}", basePath);
        getProject().getLogger().info("Modified: {}", modifiedPath);

        ArchiveFormat outputFormat = this.outputFormat.getOrNull();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }

        DiffOperation.Builder builder = DiffOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .aPath(basePath)
                .bPath(modifiedPath)
                .outputPath(outputPath, outputFormat)
                .autoHeader(autoHeader)
                .verbose(verbose)
                .summary(printSummary)
                .aPrefix(originalPrefix.get())
                .bPrefix(modifiedPrefix.get());

        if (contextLines != -1) {
            builder.context(contextLines);
        }

        CliOperation.Result<DiffOperation.DiffSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
    }

    @InputFile
    public RegularFileProperty getBase() {
        return base;
    }

    @InputFile
    public RegularFileProperty getModified() {
        return modified;
    }

    @OutputDirectory
    public DirectoryProperty getOutput() {
        return output;
    }

    @Input
    @Optional
    public Property<ArchiveFormat> getOutputFormat() {
        return outputFormat;
    }

    @Input
    @Optional
    public Property<String> getOriginalPrefix() {
        return originalPrefix;
    }

    @Input
    @Optional
    public Property<String> getModifiedPrefix() {
        return modifiedPrefix;
    }

    @Input
    @Optional
    public boolean isAutoHeader() {
        return autoHeader;
    }

    public void setAutoHeader(boolean autoHeader) {
        this.autoHeader = autoHeader;
    }

    @Input
    @Optional
    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int lines) {
        this.contextLines = lines;
    }

    @Console
    @Optional
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Console
    @Optional
    public boolean isPrintSummary() {
        return printSummary;
    }

    public void setPrintSummary(boolean printSummary) {
        this.printSummary = printSummary;
    }
}
