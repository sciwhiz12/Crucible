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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.config.MCPConfigV2;

public class ExtractMCPData extends DefaultTask {
    private final Property<String> key;
    private final RegularFileProperty config;
    private boolean allowEmpty = false;
    private final RegularFileProperty output;

    public ExtractMCPData() {
        config = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("output.srg")));
        key = getProject().getObjects().property(String.class)
                .convention("mappings");
    }

    @TaskAction
    public void run() throws IOException {
        MCPConfigV1 cfg = MCPConfigV2.getFromArchive(config.get().getAsFile());

        try (ZipFile zip = new ZipFile(config.get().getAsFile())) {
            String path = cfg.getData(key.get().split("/"));
            if (path == null && "statics".equals(key.get()))
                path = "config/static_methods.txt";

            if (path == null) {
                error("Could not find data entry for '" + key.get() + "'");
                return;
            }

            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                error("Invalid config zip, Missing path '" + path + "'");
                return;
            }

            try (OutputStream out = new FileOutputStream(output.get().getAsFile())) {
                IOUtils.copy(zip.getInputStream(entry), out);
            }
        }
    }

    private void error(String message) throws IOException {
        if (!isAllowEmpty())
            throw new IllegalStateException(message);

        File outputFile = output.get().getAsFile();
        if (outputFile.exists())
            outputFile.delete();

        outputFile.createNewFile();
    }

    @Input
    public Property<String> getKey() {
        return key;
    }

    @InputFile
    public RegularFileProperty getConfig() {
        return config;
    }

    @Input
    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }
}
