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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.util.List;

public class GenerateBinPatches extends JarExec {
    private final RegularFileProperty cleanJar;
    private final RegularFileProperty dirtyJar;
    private final RegularFileProperty srg;
    private final ConfigurableFileCollection patchSets;
    private final Property<String> side;
    private final RegularFileProperty output;
    private final MapProperty<String, File> extras;

    public GenerateBinPatches() {
        tool.set(Utils.BINPATCHER);
        args.addAll("--clean", "{clean}", "--create", "{dirty}", "--output", "{output}", "--patches", "{patches}", "--srg", "{srg}");

        cleanJar = getProject().getObjects().fileProperty();
        dirtyJar = getProject().getObjects().fileProperty();
        srg = getProject().getObjects().fileProperty();
        patchSets = getProject().getObjects().fileCollection();
        side = getProject().getObjects().property(String.class);
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file(side.getOrElse("output") + ".lzma")));
        extras = getProject().getObjects().mapProperty(String.class, File.class);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{clean}", cleanJar.get().getAsFile(),
                "{dirty}", dirtyJar.get().getAsFile(),
                "{output}", output.get().getAsFile(),
                "{srg}", srg.get().getAsFile()
                ), ImmutableMultimap.<String, Object>builder()
                        .putAll("{patches}", patchSets.getFiles()).build()
        );
    }

    @InputFile
    public RegularFileProperty getCleanJar() {
        return cleanJar;
    }

    @InputFile
    public RegularFileProperty getDirtyJar() {
        return dirtyJar;
    }

    @InputFiles
    public ConfigurableFileCollection getPatchSets() {
        return this.patchSets;
    }

    @InputFiles
    @Optional
    public MapProperty<String, File> getExtraFiles() {
        return this.extras;
    }

    @InputFile
    public RegularFileProperty getSrg() {
        return this.srg;
    }

    @Input
    @Optional
    public Property<String> getSide() {
        return this.side;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }
}
