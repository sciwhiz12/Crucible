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

package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMCPConfig extends DefaultTask {
    private final Property<String> config;
    private final RegularFileProperty output;

    public DownloadMCPConfig() {
        config = getProject().getObjects().property(String.class);
        output = getProject().getObjects().fileProperty();
    }

    @TaskAction
    public void downloadMCPConfig() throws IOException {
        File file = getConfigFile();
        File output = getOutput().get().getAsFile();

        if (output.exists()) {
            if (FileUtils.contentEquals(file, output)) {
                // NO-OP: The contents of both files are the same, we're up to date
                setDidWork(false);
                return;
            } else {
                output.delete();
            }
        }
        FileUtils.copyFile(file, output);
        setDidWork(true);
    }

    @Input
    public Property<String> getConfig() {
        return this.config;
    }

    @InputFile
    private File getConfigFile() {
        return downloadConfigFile(config.get());
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return this.output;
    }

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.manual(getProject(), config, false);
    }
}
