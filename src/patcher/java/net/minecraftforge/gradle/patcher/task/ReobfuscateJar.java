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
import com.google.common.io.Files;
import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ReobfuscateJar extends JarExec {
    private final RegularFileProperty input;
    private final RegularFileProperty srg;
    private final RegularFileProperty output;
    private boolean keepPackages = false;
    private boolean keepData = false;

    private final Provider<RegularFile> outputTemp = workDir.map(d -> d.file("output_temp.jar"));

    public ReobfuscateJar() {
        tool.set(Utils.SPECIALSOURCE);
        args.addAll("--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{srg}", "--live");

        input = getProject().getObjects().fileProperty();
        srg = getProject().getObjects().fileProperty();
        output = getProject().getObjects().fileProperty()
                .convention(workDir.map(d -> d.file("output.jar")));
    }

    @TaskAction
    public void apply() throws IOException {
        super.apply();

        try (OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile.get().getAsFile()))) {

            List<String> lines = Files.readLines(srg.get().getAsFile(), StandardCharsets.UTF_8);
            lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> l != null & !l.trim().isEmpty()).collect(Collectors.toList()); //Strip empty/comments

            Set<String> packages = new HashSet<>();
            lines.stream()
                    .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
                    .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
                    .filter(pts -> pts.length == 2)
                    .forEach(pts -> {
                        int idx = pts[0].lastIndexOf('/');
                        if (idx != -1) {
                            packages.add(pts[0].substring(0, idx + 1) + "package-info.class");
                        }
                    });

            try (ZipFile zin = new ZipFile(outputTemp.get().getAsFile());
                 ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputTemp.get().getAsFile()))) {
                for (Enumeration<? extends ZipEntry> enu = zin.entries(); enu.hasMoreElements(); ) {
                    ZipEntry entry = enu.nextElement();
                    boolean filter = entry.isDirectory() || entry.getName().startsWith("mcp/"); //Directories and MCP's annotations
                    if (!keepPackages) filter |= packages.contains(entry.getName());
                    if (!keepData) filter |= !entry.getName().endsWith(".class");

                    if (filter) {
                        log.write(("Filtered: " + entry.getName() + '\n').getBytes(StandardCharsets.UTF_8));
                        continue;
                    }
                    out.putNextEntry(entry);
                    IOUtils.copy(zin.getInputStream(entry), out);
                    out.closeEntry();
                }
            }

            outputTemp.get().getAsFile().delete();
        }
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{input}", input.get().getAsFile(),
                "{output}", outputTemp.get().getAsFile(),
                "{srg}", srg.get().getAsFile()), null);
    }

    @InputFile
    public RegularFileProperty getInput() {
        return input;
    }

    @InputFile
    public RegularFileProperty getSrg() {
        return srg;
    }

    @OutputFile
    public RegularFileProperty getOutput() {
        return output;
    }

    @Input
    public boolean getKeepPackages() {
        return this.keepPackages;
    }

    public void keepPackages() {
        this.keepPackages = true;
    }

    public void filterPackages() {
        this.keepPackages = false;
    }

    @Input
    public boolean getKeepData() {
        return this.keepData;
    }

    public void keepData() {
        this.keepData = true;
    }

    public void filterData() {
        this.keepData = false;
    }
}
