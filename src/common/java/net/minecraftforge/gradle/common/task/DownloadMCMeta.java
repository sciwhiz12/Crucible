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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.util.ManifestJson;

public class DownloadMCMeta extends DefaultTask {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    private final Property<String> mcVersion;
    private final RegularFileProperty manifest;
    private final RegularFileProperty output;

    public DownloadMCMeta() {
        mcVersion = getProject().getObjects().property(String.class);

        manifest = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("manifest.json")));
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("version.json")));
    }

    @TaskAction
    public void downloadMCMeta() throws IOException {
        try (InputStream manin = new URL(MANIFEST_URL).openStream()) {
            URL url = GSON.fromJson(new InputStreamReader(manin), ManifestJson.class).getUrl(getMCVersion().get());
            if (url != null) {
                FileUtils.copyURLToFile(url, getOutput().get().getAsFile());
            } else {
                throw new RuntimeException("Missing version from manifest: " + getMCVersion());
            }
        }
    }

    @Input
    public Property<String> getMCVersion() {
        return this.mcVersion;
    }

    // TODO: check for uses, remove if not used
    public RegularFileProperty getManifest() {
        return this.manifest;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return this.output;
    }
}
