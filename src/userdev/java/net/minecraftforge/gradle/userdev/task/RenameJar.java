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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenameJar extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty output;
    private final RegularFileProperty mappings;
    private final ConfigurableFileCollection extraMappings;

    public RenameJar() {
        tool.set(Utils.SPECIALSOURCE);
        args.addAll("--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{mappings}");

        input = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty();
        mappings = getProject().getObjects().fileProperty();
        extraMappings = getProject().getObjects().fileCollection();
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().get().getAsFile().getAbsolutePath());
        replace.put("{output}", getOutput().get().getAsFile().getAbsolutePath());
        replace.put("{mappings}", getMappings().get().getAsFile().getAbsolutePath());

        List<String> _args = new ArrayList<>();
        for (String arg : args) {
            if ("{mappings}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.add(getMappings().get().getAsFile().getAbsolutePath());

                getExtraMappings().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }

        return _args;
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

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }
}
