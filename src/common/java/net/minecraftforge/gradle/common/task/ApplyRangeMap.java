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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import net.minecraftforge.gradle.common.util.Utils;

import java.util.List;

public class ApplyRangeMap extends JarExec {
    private final ConfigurableFileCollection srgs;
    private final ConfigurableFileCollection excs;
    private final ConfigurableFileCollection sources;
    private final RegularFileProperty rangeMap;
    private final RegularFileProperty output;
    public boolean annotate = false;
    public boolean keepImports = true;

    public ApplyRangeMap() {
        tool.set(Utils.SRG2SOURCE);
        args.addAll("--apply", "--input", "{input}", "--range", "{range}", "--srg", "{srg}", "--exc", "{exc}", "--output", "{output}", "--keepImports", "{keepImports}");

        srgs = getProject().getObjects().fileCollection();
        excs = getProject().getObjects().fileCollection();
        sources = getProject().getObjects().fileCollection();
        rangeMap = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.zip")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{range}", rangeMap.get().getAsFile(),
                "{output}", output.get().getAsFile(),
                "{annotate}", annotate,
                "{keepImports}", keepImports
        ), ImmutableMultimap.<String, Object>builder()
                .putAll("{input}", sources.getFiles())
                .putAll("{srg}", srgs.getFiles())
                .putAll("{exc}", excs.getFiles()).build()
        );
    }

    @InputFiles
    public ConfigurableFileCollection getSrgFiles() {
        return this.srgs;
    }

    @InputFiles
    public ConfigurableFileCollection getSources() {
        return sources;
    }

    @InputFiles
    public ConfigurableFileCollection getExcFiles() {
        return excs;
    }

    @InputFile
    public RegularFileProperty getRangeMap() {
        return rangeMap;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }

    @Input
    public boolean getAnnotate() {
        return annotate;
    }
    public void setAnnotate(boolean value) {
        this.annotate = value;
    }

    @Input
    public boolean getKeepImports() {
        return keepImports;
    }
    public void setKeepImports(boolean value) {
        this.keepImports = value;
    }
}
