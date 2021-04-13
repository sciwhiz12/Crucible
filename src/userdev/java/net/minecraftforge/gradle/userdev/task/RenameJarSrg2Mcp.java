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

import java.util.List;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

public class RenameJarSrg2Mcp extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty output;
    private final RegularFileProperty mappings;
    private boolean signatureRemoval = false;

    public RenameJarSrg2Mcp() {
        tool.set(Utils.INSTALLERTOOLS);
        args.addAll("--task", "SRG_TO_MCP", "--input", "{input}", "--output", "{output}", "--mcp", "{mappings}", "{strip}");

        input = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty();
        mappings = getProject().getObjects().fileProperty();
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{input}", input.get().getAsFile(),
                "{output}", output.get().getAsFile(),
                "{mappings}", mappings.get().getAsFile(),
                "{strip}", signatureRemoval ? "--strip-signatures" : ""), null);
    }

    public boolean getSignatureRemoval() {
        return this.signatureRemoval;
    }

    public void setSignatureRemoval(boolean value) {
        this.signatureRemoval = value;
    }

    @InputFile
    public RegularFileProperty getMappings() {
        return mappings;
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
