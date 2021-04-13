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

package net.minecraftforge.gradle.userdev.task;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

public class RenameJarInPlace extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty mappings;
    private final ConfigurableFileCollection extraMappings;
    private final Provider<RegularFile> temp = getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("output.jar"));

    public RenameJarInPlace() {
        tool.set(Utils.SPECIALSOURCE);
        args.addAll("--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{mappings}", "--live");
        this.getOutputs().upToDateWhen(task -> false);

        input = getProject().getObjects().fileProperty();
        mappings = getProject().getObjects().fileProperty();
        extraMappings = getProject().getObjects().fileCollection();
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{input}", input.get().getAsFile(),
                "{output}", temp.get().getAsFile()
                ), ImmutableMultimap.<String, Object>builder()
                        .put("{mappings}", mappings.get().getAsFile())
                        .putAll("{mappings}", extraMappings.getFiles()).build()
        );
    }

    @Override
    @TaskAction
    public void apply() throws IOException {
        File temp = this.temp.get().getAsFile();
        if (!temp.getParentFile().exists())
            temp.getParentFile().mkdirs();

        super.apply();

        FileUtils.copyFile(temp, getInput().get().getAsFile());
    }

    @InputFile
    public RegularFileProperty getMappings() {
        return mappings;
    }

    @Optional
    @InputFiles
    public ConfigurableFileCollection getExtraMappings() {
        return extraMappings;
    }

    @InputFile
    public RegularFileProperty getInput() {
        return input;
    }
}
