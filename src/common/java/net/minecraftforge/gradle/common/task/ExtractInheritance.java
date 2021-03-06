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

import java.util.List;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import net.minecraftforge.gradle.common.util.Utils;

public class ExtractInheritance extends JarExec {
    private final RegularFileProperty input;
    private final ConfigurableFileCollection libraries;
    private final RegularFileProperty output;

    public ExtractInheritance() {
        tool.set(Utils.INSTALLERTOOLS);
        args.addAll("--task", "extract_inheritance", "--input", "{input}", "--output", "{output}");

        input = getProject().getObjects().fileProperty();
        libraries = getProject().getObjects().fileCollection();
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.json")));
    }
    @Override
    protected List<String> filterArgs(List<String> args) {
        List<String> newArgs = replaceArgs(args, ImmutableMap.of(
                "{input}", input.get().getAsFile(),
                "{output}", output.get().getAsFile()), null);
        getLibraries().forEach(f -> {
            newArgs.add("--lib");
            newArgs.add(f.getAbsolutePath());
        });
        return newArgs;
    }


    @InputFile
    public RegularFileProperty getInput(){ return input; }

    @InputFiles
    public ConfigurableFileCollection getLibraries() { return libraries; }

    @OutputFile
    public RegularFileProperty getOutput(){ return output; }
}
