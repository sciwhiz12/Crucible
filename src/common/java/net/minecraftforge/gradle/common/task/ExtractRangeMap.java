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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.util.Utils;

public class ExtractRangeMap extends JarExec {
    private final ConfigurableFileCollection sources;
    private final ConfigurableFileCollection dependencies;
    private final RegularFileProperty output;
    private final Property<String> sourceCompatibility;
    private boolean batch = true;

    public ExtractRangeMap() {
        tool.set(Utils.SRG2SOURCE);
        args.addAll("--extract", "--source-compatibility", "{compat}", "--output", "{output}", "--lib", "{library}", "--input", "{input}", "--batch", "{batched}");

        sources = getProject().getObjects().fileCollection();
        dependencies = getProject().getObjects().fileCollection();
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.txt")));
        sourceCompatibility = getProject().getObjects().property(String.class)
                .convention("1.8");
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        Map<String, String> replace = new HashMap<>();
        replace.put("{compat}", getSourceCompatibility().get());
        replace.put("{output}", getOutput().get().getAsFile().getAbsolutePath());
        replace.put("{batched}", getBatch() ? "true" : "false");

        List<String> _args = new ArrayList<>();
        for (String arg : args) {
            if ("{library}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getDependencies().forEach(f -> {
                    _args.add(prefix);
                    _args.add(f.getAbsolutePath());
                });
            } else if ("{input}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getSources().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }
        return _args;
    }

    @InputFiles
    public ConfigurableFileCollection getSources() {
        return sources;
    }

    @InputFiles
    public ConfigurableFileCollection getDependencies() {
        return dependencies;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }

    @Input
    public Property<String> getSourceCompatibility() {
        return this.sourceCompatibility;
    }

    @Input
    public boolean getBatch() {
        return this.batch;
    }
    public void setBatch(boolean value) {
        this.batch = value;
    }
}
