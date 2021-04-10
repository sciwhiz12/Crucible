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
import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.InputPath;
import codechicken.diffpatch.util.OutputPath;
import codechicken.diffpatch.util.archiver.ArchiveFormat;

/**
 * Bakes Auto-Header patch files.
 */
public class BakePatches extends DefaultTask {
    private final DirectoryProperty input;
    private final RegularFileProperty output;

    public BakePatches() {
        input = getProject().getObjects().directoryProperty();
        output = getProject().getObjects().fileProperty();
    }

    @TaskAction
    public void doTask() throws IOException {
        File output = this.output.get().getAsFile();
        ArchiveFormat outputFormat = ArchiveFormat.findFormat(output.getName());
        PatchOperation.bakePatches(new InputPath.FilePath(input.get().getAsFile().toPath(), null), new OutputPath.FilePath(output.toPath(), outputFormat));
    }

    @InputDirectory
    public DirectoryProperty getInput() {
        return input;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }
}
