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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, String> replace = new HashMap<>();
        replace.put("{range}", getRangeMap().get().getAsFile().getAbsolutePath());
        replace.put("{output}", getOutput().get().getAsFile().getAbsolutePath());
        replace.put("{annotate}", getAnnotate() ? "true" : "false");
        replace.put("{keepImports}", getKeepImports() ? "true" : "false");

        List<String> _args = new ArrayList<>();
        for (String arg : args) {
            if ("{input}".equals(arg))
                expand(_args, getSources().getFiles());
            else if ("{srg}".equals(arg))
                expand(_args, getSrgFiles().getFiles());
            else if ("{exc}".equals(arg))
                expand(_args, getExcFiles().getFiles());
            else
                _args.add(replace.getOrDefault(arg, arg));
        }
        return _args;
    }

    private void expand(List<String> _args, Collection<File> files)
    {
        String prefix = _args.get(_args.size() - 1);
        _args.remove(_args.size() - 1);
        files.forEach(f -> {
            _args.add(prefix);
            _args.add(f.getAbsolutePath());
        });
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
