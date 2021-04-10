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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

public class DynamicJarExec extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty output;
    private final MapProperty<String, File> data;

    public DynamicJarExec() {
        input = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty()
                .convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
        data = getProject().getObjects().mapProperty(String.class, File.class);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().get().getAsFile().getAbsolutePath());
        replace.put("{output}", getOutput().get().getAsFile().getAbsolutePath());
        data.get().forEach((key,value) -> replace.put('{' + key + '}', value.getAbsolutePath()));

        return args.stream().map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

	@InputFiles
	@Optional
	public MapProperty<String, File> getData() {
		return this.data;
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
