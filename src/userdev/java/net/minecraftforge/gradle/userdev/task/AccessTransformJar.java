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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.util.List;

public class AccessTransformJar extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty output;
    private final ConfigurableFileCollection ats;

    public AccessTransformJar() {
        tool.set(Utils.ACCESSTRANSFORMER); // AT spec *should* be standardized, it has been for years. So we *shouldn't* need to configure this.
        args.addAll("--inJar", "{input}", "--outJar", "{output}", "--logFile", "accesstransform.log");

        input = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty();
        ats = getProject().getObjects().fileCollection();
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        List<String> newArgs = replaceArgs(args, ImmutableMap.of(
                        "{input}", input.get().getAsFile().getAbsolutePath(),
                        "{output}", output.get().getAsFile().getAbsolutePath()), null);
        ats.forEach(f -> {
            newArgs.add("--atFile");
            newArgs.add(f.getAbsolutePath());
        });
        return newArgs;
    }

    @InputFiles
    public ConfigurableFileCollection getAts() {
        return ats;
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
