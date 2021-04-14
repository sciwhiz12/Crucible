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

import java.io.File;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import codechicken.diffpatch.util.archiver.ArchiveFormat;

public class ApplyPatches extends DefaultTask {
    private final Property<File> base;
    private final DirectoryProperty patches;
    private final RegularFileProperty output;
    private final Property<File> rejects;
    private final Property<ArchiveFormat> outputFormat;
    private final Property<ArchiveFormat> rejectsFormat;
    private final Property<PatchMode> patchMode;
    private final Property<String> patchesPrefix;
    private final Property<String> originalPrefix;
    private final Property<String> modifiedPrefix;

    private float minFuzzQuality = -1;
    private int maxFuzzOffset = -1;
    private boolean verbose = false;
    private boolean printSummary = false;
    private boolean failOnError = true;

    public ApplyPatches() {
        base = getProject().getObjects().property(File.class);
        patches = getProject().getObjects().directoryProperty();
        output = getProject().getObjects().fileProperty();
        rejects = getProject().getObjects().property(File.class);
        outputFormat = getProject().getObjects().property(ArchiveFormat.class);
        rejectsFormat = getProject().getObjects().property(ArchiveFormat.class);
        patchMode = getProject().getObjects().property(PatchMode.class)
                .convention(PatchMode.EXACT);
        patchesPrefix = getProject().getObjects().property(String.class)
                .convention("");
        originalPrefix = getProject().getObjects().property(String.class)
                .convention("a/");
        modifiedPrefix = getProject().getObjects().property(String.class)
                .convention("b/");
    }

    @TaskAction
    public void doTask() throws Exception {
        if (!patches.isPresent()) {
            FileUtils.copyFile(base.get(), output.get().getAsFile());
            return;
        }

        Path outputPath = output.get().getAsFile().toPath();
        ArchiveFormat outputFormat = this.outputFormat.getOrNull();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }

        Path rejectsPath = rejects.map(File::toPath).getOrNull();
        ArchiveFormat rejectsFormat = this.outputFormat.getOrNull();
        if (rejectsFormat == null) {
            rejectsFormat = ArchiveFormat.findFormat(rejectsPath.getFileName());
        }

        PatchOperation.Builder builder = PatchOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .basePath(base.get().toPath())
                .patchesPath(patches.get().getAsFile().toPath())
                .outputPath(outputPath, outputFormat)
                .rejectsPath(rejectsPath, rejectsFormat)
                .verbose(verbose)
                .summary(printSummary)
                .mode(patchMode.get())
                .aPrefix(originalPrefix.get())
                .bPrefix(modifiedPrefix.get())
                .patchesPrefix(patchesPrefix.get());
        if (minFuzzQuality != -1) {
            builder.minFuzz(minFuzzQuality);
        }
        if (maxFuzzOffset != -1) {
            builder.maxOffset(maxFuzzOffset);
        }

        CliOperation.Result<PatchOperation.PatchesSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
        if (exit != 0 && isFailOnError()) {
            throw new RuntimeException("Patches failed to apply.");
        }
    }

    @Input
    public Property<File> getBase() {
        return base;
    }

    @InputDirectory
    @Optional
    public DirectoryProperty getPatches() {
        return patches;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }

    @Optional
    public Property<File> getRejects() {
        return rejects;
    }

    @Input
    @Optional
    public Property<ArchiveFormat> getOutputFormat() {
        return outputFormat;
    }

    @Input
    @Optional
    public Property<ArchiveFormat> getRejectsFormat() {
        return rejectsFormat;
    }

    @Input
    @Optional
    public Property<PatchMode> getPatchMode() {
        return patchMode;
    }

    @Input
    @Optional
    public Property<String> getPatchesPrefix() {
        return patchesPrefix;
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
    public float getMinFuzzQuality() {
        return minFuzzQuality;
    }

    public void setMinFuzzQuality(float minFuzzQuality) {
        this.minFuzzQuality = minFuzzQuality;
    }

    @Input
    @Optional
    public int getMaxFuzzOffset() {
        return maxFuzzOffset;
    }

    public void setMaxFuzzOffset(int maxFuzzOffset) {
        this.maxFuzzOffset = maxFuzzOffset;
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

    @Input
    @Optional
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }
}
