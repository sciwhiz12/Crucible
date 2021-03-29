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

package net.minecraftforge.gradle.patcher;

import codechicken.diffpatch.util.PatchMode;
import com.google.common.collect.Lists;

import net.minecraftforge.gradle.common.FGBasePlugin;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DownloadMCMeta;
import net.minecraftforge.gradle.common.task.DynamicJarExec;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.task.ExtractZip;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionFactory;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.task.SetupMCP;
import net.minecraftforge.gradle.patcher.task.CreateFakeSASPatches;
import net.minecraftforge.gradle.mcp.task.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.patcher.task.GenerateBinPatches;
import net.minecraftforge.gradle.common.task.ApplyMappings;
import net.minecraftforge.gradle.patcher.task.ApplyPatches;
import net.minecraftforge.gradle.common.task.ApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.CreateExc;
import net.minecraftforge.gradle.common.task.ExtractExistingFiles;
import net.minecraftforge.gradle.common.task.ExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.BakePatches;
import net.minecraftforge.gradle.patcher.task.FilterNewJar;
import net.minecraftforge.gradle.patcher.task.GeneratePatches;
import net.minecraftforge.gradle.patcher.task.GenerateUserdevConfig;
import net.minecraftforge.gradle.patcher.task.ReobfuscateJar;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PatcherPlugin implements Plugin<Project> {
    public static final String MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME = "minecraftImplementation";

    // Used to set the rejects output for applyPatches, not related to updateMappings
    public static final String UPDATING_PROPERTY = "UPDATING";

    public static final String UPDATE_MAPPINGS_PROPERTY = "UPDATE_MAPPINGS";
    public static final String UPDATE_MAPPINGS_CHANNEL_PROPERTY = "UPDATE_MAPPINGS_CHANNEL";
    private static final String DEFAULT_UPDATE_MAPPINGS_CHANNEL = "snapshot";

    // Normal patcher tasks
    public static final String DOWNLOAD_MAPPINGS_TASK_NAME = "downloadMappings";
    public static final String DOWNLOAD_MC_META_TASK_NAME = "downloadMCMeta";
    public static final String EXTRACT_NATIVES_TASK_NAME = "extractNatives";
    public static final String APPLY_PATCHES_TASK_NAME = "applyPatches";
    public static final String APPLY_MAPPINGS_TASK_NAME = "srg2mcp";
    public static final String EXTRACT_MAPPED_TASK_NAME = "extractMapped";
    public static final String CREATE_MCP_TO_SRG_TASK_NAME = "createMcp2Srg";
    public static final String CREATE_MCP_TO_OBF_TASK_NAME = "createMcp2Obf";
    public static final String CREATE_SRG_TO_MCP_TASK_NAME = "createSrg2Mcp";
    public static final String CREATE_EXC_TASK_NAME = "createExc";
    public static final String EXTRACT_RANGE_MAP_TASK_NAME = "extractRangeMap";
    public static final String APPLY_RANGE_MAP_TASK_NAME = "applyRangeMap";
    public static final String APPLY_RANGE_MAP_BASE_TASK_NAME = "applyRangeMapBase";
    public static final String GENERATE_PATCHES_TASK_NAME = "genPatches";
    public static final String BAKE_PATCHES_TASK_NAME = "bakePatches";
    public static final String DOWNLOAD_ASSETS_TASK_NAME = "downloadAssets";
    public static final String REOBFUSCATE_JAR_TASK_NAME = "reobfJar";
    public static final String GENERATE_JOINED_BIN_PATCHES_TASK_NAME = "genJoinedBinPatches";
    public static final String GENERATE_CLIENT_BIN_PATCHES_TASK_NAME = "genClientBinPatches";
    public static final String GENERATE_SERVER_BIN_PATCHES_TASK_NAME = "genServerBinPatches";
    public static final String GENERATE_BIN_PATCHES_TASK_NAME = "genBinPatches";
    public static final String FILTER_NEW_JAR_TASK_NAME = "filterJarNew";
    public static final String SOURCES_JAR_TASK_NAME = "sourcesJar";
    public static final String UNIVERSAL_JAR_TASK_NAME = "universalJar";
    public static final String USERDEV_JAR_TASK_NAME = "userdevJar";
    public static final String GENERATE_USERDEV_CONFIG_TASK_NAME = "userdevConfig";
    public static final String RELEASE_TASK_NAME = "release";

    public static final String EXTRACT_SRG_TASK_NAME = "extractSrg";
    public static final String EXTRACT_STATIC_TASK_NAME = "extractStatic";
    public static final String EXTRACT_CONSTRUCTORS_TASK_NAME = "extractConstructors";
    public static final String CREATE_FAKE_SAS_PATCHES_TASK_NAME = "createFakeSASPatches";
    public static final String APPLY_MAPPINGS_CLEAN_TASK_NAME = "srg2mcpClean";
    public static final String PATCHED_ZIP_TASK_NAME = "patchedZip";

    // updateMappings tasks
    public static final String DOWNLOAD_NEW_MAPPINGS_TASK_NAME = "downloadMappingsNew";
    public static final String APPLY_NEW_MAPPINGS_TASK_NAME = "srg2mcpNew";
    public static final String EXTRACT_NEW_MAPPED_TASK_NAME = "extractMappedNew";
    public static final String UPDATE_MAPPINGS_TASK_NAME = "updateMappings";

    @Override
    public void apply(@Nonnull Project project) {
        project.getPlugins().apply(FGBasePlugin.class);

        final PatcherExtension extension = project.getExtensions().create(PatcherExtension.class, PatcherExtension.EXTENSION_NAME, PatcherExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        final File natives_folder = project.file("build/natives/");

        Configuration mcImplementation = project.getConfigurations().maybeCreate(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME);
        mcImplementation.setCanBeResolved(true);
        project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(mcImplementation);

        Jar jarConfig = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
        JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);

        TaskProvider<DownloadMCPMappings> dlMappingsConfig = project.getTasks().register(DOWNLOAD_MAPPINGS_TASK_NAME, DownloadMCPMappings.class);
        TaskProvider<DownloadMCMeta> dlMCMetaConfig = project.getTasks().register(DOWNLOAD_MC_META_TASK_NAME, DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register(EXTRACT_NATIVES_TASK_NAME, ExtractNatives.class);
        TaskProvider<ApplyPatches> applyPatches = project.getTasks().register(APPLY_PATCHES_TASK_NAME, ApplyPatches.class);
        TaskProvider<ApplyMappings> toMCPConfig = project.getTasks().register(APPLY_MAPPINGS_TASK_NAME, ApplyMappings.class);
        TaskProvider<ExtractZip> extractMapped = project.getTasks().register(EXTRACT_MAPPED_TASK_NAME, ExtractZip.class);
        TaskProvider<GenerateSRG> createMcp2Srg = project.getTasks().register(CREATE_MCP_TO_SRG_TASK_NAME, GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcp2Obf = project.getTasks().register(CREATE_MCP_TO_OBF_TASK_NAME, GenerateSRG.class);
        TaskProvider<GenerateSRG> createSrg2Mcp = project.getTasks().register(CREATE_SRG_TO_MCP_TASK_NAME, GenerateSRG.class);
        TaskProvider<CreateExc> createExc = project.getTasks().register(CREATE_EXC_TASK_NAME, CreateExc.class);
        TaskProvider<ExtractRangeMap> extractRangeConfig = project.getTasks().register(EXTRACT_RANGE_MAP_TASK_NAME, ExtractRangeMap.class);
        TaskProvider<ApplyRangeMap> applyRangeConfig = project.getTasks().register(APPLY_RANGE_MAP_TASK_NAME, ApplyRangeMap.class);
        TaskProvider<ApplyRangeMap> applyRangeBaseConfig = project.getTasks().register(APPLY_RANGE_MAP_BASE_TASK_NAME, ApplyRangeMap.class);
        TaskProvider<GeneratePatches> genPatches = project.getTasks().register(GENERATE_PATCHES_TASK_NAME, GeneratePatches.class);
        TaskProvider<BakePatches> bakePatches = project.getTasks().register(BAKE_PATCHES_TASK_NAME, BakePatches.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register(DOWNLOAD_ASSETS_TASK_NAME, DownloadAssets.class);
        TaskProvider<ReobfuscateJar> reobfJar = project.getTasks().register(REOBFUSCATE_JAR_TASK_NAME, ReobfuscateJar.class);
        TaskProvider<GenerateBinPatches> genJoinedBinPatches = project.getTasks().register(GENERATE_JOINED_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genClientBinPatches = project.getTasks().register(GENERATE_CLIENT_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genServerBinPatches = project.getTasks().register(GENERATE_SERVER_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
        TaskProvider<DefaultTask> genBinPatches = project.getTasks().register(GENERATE_BIN_PATCHES_TASK_NAME, DefaultTask.class);
        TaskProvider<FilterNewJar> filterNew = project.getTasks().register(FILTER_NEW_JAR_TASK_NAME, FilterNewJar.class);
        TaskProvider<Jar> sourcesJar = project.getTasks().register(SOURCES_JAR_TASK_NAME, Jar.class);
        TaskProvider<Jar> universalJar = project.getTasks().register(UNIVERSAL_JAR_TASK_NAME, Jar.class);
        TaskProvider<Jar> userdevJar = project.getTasks().register(USERDEV_JAR_TASK_NAME, Jar.class);
        TaskProvider<GenerateUserdevConfig> userdevConfig = project.getTasks().register(GENERATE_USERDEV_CONFIG_TASK_NAME, GenerateUserdevConfig.class, project);
        TaskProvider<DefaultTask> release = project.getTasks().register(RELEASE_TASK_NAME, DefaultTask.class);
        TaskProvider<DefaultTask> hideLicense = project.getTasks().register(MojangLicenseHelper.HIDE_LICENSE_TASK_NAME, DefaultTask.class);
        TaskProvider<DefaultTask> showLicense = project.getTasks().register(MojangLicenseHelper.SHOW_LICENSE_TASK_NAME, DefaultTask.class);

        new BaseRepo.Builder()
            .add(MCPRepo.create(project))
            .add(MinecraftRepo.create(project))
            .attach(project);

        hideLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.hide(project, extension.getMappingChannel().get(), extension.getMappingVersion().get());
            });
        });

        showLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.show(project, extension.getMappingChannel().get(), extension.getMappingVersion().get());
            });
        });

        release.configure(task -> {
            task.dependsOn(sourcesJar, universalJar, userdevJar);
        });
        dlMappingsConfig.configure(task -> {
            task.setMappings(extension.getMappings().get());
        });
        extractNatives.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.getMeta().set(dlMCMetaConfig.flatMap(DownloadMCMeta::getOutput));
            task.getOutput().set(natives_folder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.setMeta(dlMCMetaConfig.get().getOutput().getAsFile().get());
        });
        applyPatches.configure(task -> {
            task.setOutput(project.file("build/" + task.getName() + "/output.zip"));
            task.setRejects(project.file("build/" + task.getName() + "/rejects.zip"));
            task.setPatches(extension.getPatches().get().getAsFile());
            task.setPatchMode(PatchMode.ACCESS);
            if (project.hasProperty(UPDATING_PROPERTY)) {
                task.setPatchMode(PatchMode.FUZZY);
                task.setRejects(project.file("rejects/"));
                task.setFailOnError(false);
            }
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyPatches);
            task.getInput().set(applyPatches.get().getOutput());
            task.getMappings().set(dlMappingsConfig.get().getOutput());
            task.getLambdas().set(false);
        });
        extractMapped.configure(task -> {
            task.dependsOn(toMCPConfig);
            task.setZip(toMCPConfig.get().getOutput().get().getAsFile());
            task.setOutput(extension.getPatchedSrc().get().getAsFile());
        });
        extractRangeConfig.configure(task -> {
            task.dependsOn(jarConfig);
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.addDependencies(jarConfig.getArchiveFile().get().getAsFile());
        });
        createMcp2Srg.configure(task -> {
            task.setReverse(true);
        });
        createSrg2Mcp.configure(task -> {
            task.setReverse(false);
        });
        createMcp2Obf.configure(task -> {
            task.setNotch(true);
            task.setReverse(true);
        });
        createExc.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
        });

        applyRangeConfig.configure(task -> {
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
        });
        applyRangeBaseConfig.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
        });
        genPatches.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.setOutput(extension.getPatches().get().getAsFile());
        });
        bakePatches.configure(task -> {
            task.dependsOn(genPatches);
            task.setInput(extension.getPatches().get().getAsFile());
            task.setOutput(new File(task.getTemporaryDir(), "output.zip"));
        });

        reobfJar.configure(task -> {
            task.dependsOn(jarConfig, dlMappingsConfig);
            task.setInput(jarConfig.getArchiveFile().get().getAsFile());
            task.setClasspath(project.getConfigurations().getByName(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME));
        });
        genJoinedBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.getPatches().get().getAsFile());
            task.setSide("joined");
        });
        genClientBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.getPatches().get().getAsFile());
            task.setSide("client");
        });
        genServerBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.getPatches().get().getAsFile());
            task.setSide("server");
        });
        genBinPatches.configure(task -> {
            task.dependsOn(genJoinedBinPatches.get(), genClientBinPatches.get(), genServerBinPatches.get());
        });
        filterNew.configure(task -> {
            task.dependsOn(reobfJar);
            task.setInput(reobfJar.get().getOutput());
        });
        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        sourcesJar.configure(task -> {
            task.dependsOn(applyRangeConfig);
            task.from(project.zipTree(applyRangeConfig.get().getOutput()));
            task.getArchiveClassifier().set("sources");
        });
        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        universalJar.configure(task -> {
            task.dependsOn(filterNew);
            task.from(project.zipTree(filterNew.get().getOutput()));
            task.from(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getResources());
            task.getArchiveClassifier().set("universal");
        });
        /*UserDev:
         * config.json
         * joined.lzma
         * sources.jar
         * patches/
         *   net/minecraft/item/Item.java.patch
         * ats/
         *   at1.cfg
         *   at2.cfg
         */
        userdevJar.configure(task -> {
            task.dependsOn(userdevConfig, genJoinedBinPatches, sourcesJar, bakePatches);
            task.setOnlyIf(t -> extension.isSrgPatches());
            task.from(userdevConfig.get().getOutput(), e -> {
                e.rename(f -> "config.json");
            });
            task.from(genJoinedBinPatches.get().getOutput(), e -> {
                e.rename(f -> "joined.lzma");
            });
            task.from(project.zipTree(bakePatches.get().getOutput()), e -> {
                e.into("patches/");
            });
            task.getArchiveClassifier().set("userdev");
        });

        final boolean doingUpdate = project.hasProperty(UPDATE_MAPPINGS_PROPERTY);
        final String updateVersion = doingUpdate ? (String)project.property(UPDATE_MAPPINGS_PROPERTY) : null;
        final String updateChannel = doingUpdate
            ? (project.hasProperty(UPDATE_MAPPINGS_CHANNEL_PROPERTY) ? (String)project.property(UPDATE_MAPPINGS_CHANNEL_PROPERTY) : DEFAULT_UPDATE_MAPPINGS_CHANNEL)
            : null;
        if (doingUpdate) {
            TaskProvider<DownloadMCPMappings> dlMappingsNew = project.getTasks().register(DOWNLOAD_NEW_MAPPINGS_TASK_NAME, DownloadMCPMappings.class);
            dlMappingsNew.get().setMappings(updateChannel + '_' + updateVersion);

            TaskProvider<ApplyMappings> toMCPNew = project.getTasks().register(APPLY_NEW_MAPPINGS_TASK_NAME, ApplyMappings.class);
            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew.get(), applyRangeConfig.get());
                task.getInput().set(applyRangeConfig.get().getOutput());
                task.getMappings().set(dlMappingsConfig.get().getOutput());
                task.getLambdas().set(false);
            });

            TaskProvider<ExtractExistingFiles> extractMappedNew = project.getTasks().register(EXTRACT_NEW_MAPPED_TASK_NAME, ExtractExistingFiles.class);
            extractMappedNew.configure(task -> {
                task.getArchive().set(toMCPNew.flatMap(ApplyMappings::getOutput));
            });

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register(UPDATE_MAPPINGS_TASK_NAME, DefaultTask.class);
            updateMappings.get().dependsOn(extractMappedNew.get());
        }

        project.afterEvaluate(p -> {
            //Add PatchedSrc to a main sourceset and build range tasks
            SourceSet mainSource = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            applyRangeConfig.get().setSources(mainSource.getJava().getSrcDirs().stream().filter(f -> !f.equals(extension.getPatchedSrc().get().getAsFile())).collect(Collectors.toList()));
            applyRangeBaseConfig.get().setSources(extension.getPatchedSrc().get().getAsFile());
            mainSource.java(v -> {
                v.srcDir(extension.getPatchedSrc().get().getAsFile());
            });

            if (doingUpdate) {
                ExtractExistingFiles extract = (ExtractExistingFiles)p.getTasks().getByName(EXTRACT_NEW_MAPPED_TASK_NAME);
                for (File dir : mainSource.getJava().getSrcDirs()) {
                    if (dir.equals(extension.getPatchedSrc().get().getAsFile())) //Don't overwrite the patched code, re-setup the project.
                        continue;
                    extract.getTargets().from(dir);
                }
            }

            //mainSource.resources(v -> {
            //}); //TODO: Asset downloading, needs asset index from json.
            //javaConv.getSourceSets().stream().forEach(s -> extractRangeConfig.get().addSources(s.getJava().getSrcDirs()));
            // Only add main source, as we inject the patchedSrc into it as a sourceset.
            extractRangeConfig.get().addSources(mainSource.getJava().getSrcDirs());
            extractRangeConfig.get().addDependencies(javaCompile.getClasspath());

            if (extension.getPatches().isPresent() && !extension.getPatches().get().getAsFile().exists()) { //Auto-make folders so that gradle doesnt explode some tasks.
                extension.getPatches().get().getAsFile().mkdirs();
            }

            if (extension.getPatches().isPresent()) {
                sourcesJar.get().dependsOn(genPatches);
                sourcesJar.get().from(genPatches.get().getOutput(), e -> {
                    e.into("patches/");
                });
            }
            TaskProvider<DynamicJarExec> procConfig = extension.getProcessor() == null ? null : project.getTasks().register("postProcess", DynamicJarExec.class);

            if (extension.getParent().isPresent()) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                Project parent = extension.getParent().get();
                TaskContainer tasks = parent.getTasks();
                MCPPlugin mcp = parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin patcher = parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (mcp != null) {
                    MojangLicenseHelper.displayWarning(p, extension.getMappingChannel().get(), extension.getMappingVersion().get(), updateChannel, updateVersion);
                    SetupMCP setupMCP = (SetupMCP) tasks.getByName(MCPPlugin.SETUP_MCP_TASK_NAME);

                    if (procConfig != null) {
                        procConfig.get().dependsOn(setupMCP);
                        procConfig.get().setInput(setupMCP.getOutput());
                        procConfig.get().setTool(extension.getProcessor().getVersion());
                        procConfig.get().setArgs(extension.getProcessor().getArgs());
                        extension.getProcessorData().get().forEach((key, value) -> procConfig.get().setData(key, value));
                    }

                    if (!extension.getCleanSrc().isPresent()) {
                        if (procConfig != null) {
                            extension.getCleanSrc().set(procConfig.get().getOutput());
                            applyPatches.get().dependsOn(procConfig);
                            genPatches.get().dependsOn(procConfig);
                        } else {
                            extension.getCleanSrc().set(setupMCP.getOutput());
                            applyPatches.get().dependsOn(setupMCP);
                            genPatches.get().dependsOn(setupMCP);
                        }
                    }
                    if (applyPatches.get().getBase() == null) {
                        applyPatches.get().setBase(extension.getCleanSrc().get().getAsFile());
                    }
                    if (genPatches.get().getBase() == null) {
                        genPatches.get().setBase(extension.getCleanSrc().get().getAsFile());
                    }

                    DownloadMCPConfig dlMCP = (DownloadMCPConfig)tasks.getByName(MCPPlugin.DOWNLOAD_MCPCONFIG_TASK_NAME);

                    if (createMcp2Srg.get().getSrg() == null) { //TODO: Make extractMCPData macro
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register(EXTRACT_SRG_TASK_NAME, ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().getConfig().set(dlMCP.getOutput());
                        createMcp2Srg.get().setSrg(ext.get().getOutput().get().getAsFile());
                        createMcp2Srg.get().dependsOn(ext);
                    }

                    if (createExc.get().getSrg() == null) {
                        createExc.get().setSrg(createMcp2Srg.get().getSrg());
                        createExc.get().dependsOn(createMcp2Srg);
                    }

                    if (createExc.get().getStatics() == null) {
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register(EXTRACT_STATIC_TASK_NAME, ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().getConfig().set(dlMCP.getOutput());
                        ext.get().getKey().set("statics");
                        ext.get().setAllowEmpty(true);
                        ext.get().getOutput().set(project.getLayout().getBuildDirectory().dir(ext.getName()).map(d -> d.file("output.txt")));
                        createExc.get().setStatics(ext.get().getOutput().get().getAsFile());
                        createExc.get().dependsOn(ext);
                    }

                    if (createExc.get().getConstructors() == null) {
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register(EXTRACT_CONSTRUCTORS_TASK_NAME, ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().getConfig().set(dlMCP.getOutput());
                        ext.get().getKey().set("constructors");
                        ext.get().setAllowEmpty(true);
                        ext.get().getOutput().set(project.getLayout().getBuildDirectory().dir(ext.getName()).map(d -> d.file("output.txt")));
                        createExc.get().setConstructors(ext.get().getOutput().get().getAsFile());
                        createExc.get().dependsOn(ext);
                    }
                } else if (patcher != null) {
                    PatcherExtension pExt = parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    ApplyPatches parentApply = (ApplyPatches) tasks.getByName(applyPatches.get().getName());
                    if (procConfig != null) {
                        procConfig.get().dependsOn(parentApply);
                        procConfig.get().setInput(parentApply.getOutput());
                        procConfig.get().setTool(extension.getProcessor().getVersion());
                        procConfig.get().setArgs(extension.getProcessor().getArgs());
                        extension.getProcessorData().get().forEach((key, value) -> procConfig.get().setData(key, value));
                    }

                    if (!extension.getCleanSrc().isPresent()) {
                        if (procConfig != null) {
                            extension.getCleanSrc().set(procConfig.get().getOutput());
                            applyPatches.get().dependsOn(procConfig);
                            genPatches.get().dependsOn(procConfig);
                        } else {
                            extension.getCleanSrc().set(parentApply.getOutput());
                            applyPatches.get().dependsOn(parentApply);
                            genPatches.get().dependsOn(parentApply);
                        }
                    }
                    if (applyPatches.get().getBase() == null) {
                        applyPatches.get().setBase(extension.getCleanSrc().get().getAsFile());
                    }
                    if (genPatches.get().getBase() == null) {
                        genPatches.get().setBase(extension.getCleanSrc().get().getAsFile());
                    }

                    if (createMcp2Srg.get().getSrg() == null) {
                        ExtractMCPData extract = ((ExtractMCPData)tasks.getByName(EXTRACT_SRG_TASK_NAME));
                        if (extract != null) {
                            createMcp2Srg.get().setSrg(extract.getOutput().get().getAsFile());
                            createMcp2Srg.get().dependsOn(extract);
                        } else {
                            GenerateSRG task = (GenerateSRG)tasks.getByName(createMcp2Srg.get().getName());
                            createMcp2Srg.get().setSrg(task.getSrg());
                            createMcp2Srg.get().dependsOn(task);
                        }
                    }

                    if (createExc.get().getSrg() == null) { //TODO: Make a macro for Srg/Static/Constructors
                        ExtractMCPData extract = ((ExtractMCPData)tasks.getByName(EXTRACT_SRG_TASK_NAME));
                        if (extract != null) {
                            createExc.get().setSrg(extract.getOutput().get().getAsFile());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setSrg(task.getSrg());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getStatics() == null) {
                        ExtractMCPData extract = ((ExtractMCPData) tasks.getByName(EXTRACT_STATIC_TASK_NAME));
                        if (extract != null) {
                            createExc.get().setStatics(extract.getOutput().get().getAsFile());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc) tasks.getByName(createExc.get().getName());
                            createExc.get().setStatics(task.getStatics());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getConstructors() == null) {
                        ExtractMCPData extract = ((ExtractMCPData) tasks.getByName(EXTRACT_CONSTRUCTORS_TASK_NAME));
                        if (extract != null) {
                            createExc.get().setConstructors(extract.getOutput().get().getAsFile());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc) tasks.getByName(createExc.get().getName());
                            createExc.get().setConstructors(task.getConstructors());
                            createExc.get().dependsOn(task);
                        }
                    }
                    for (TaskProvider<GenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                        GenerateBinPatches pgen = (GenerateBinPatches) tasks.getByName(task.get().getName());
                        for (File patches : pgen.getPatchSets()) {
                            task.get().addPatchSet(patches);
                        }
                    }

                    filterNew.get().dependsOn(tasks.getByName(JavaPlugin.JAR_TASK_NAME));
                    filterNew.get().addBlacklist(((Jar) tasks.getByName(JavaPlugin.JAR_TASK_NAME)).getArchiveFile().get().getAsFile());
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }

                if (dlMappingsConfig.get().getMappings() == null) {
                    dlMappingsConfig.get().setMappings(extension.getMappings().get());
                }

                for (TaskProvider<GenerateSRG> genSrg : Arrays.asList(createMcp2Srg, createSrg2Mcp, createMcp2Obf)) {
                    genSrg.get().dependsOn(dlMappingsConfig);
                    if (genSrg.get().getMappings() == null) {
                        genSrg.get().setMappings(dlMappingsConfig.get().getMappings());
                    }
                }

                if (createMcp2Obf.get().getSrg() == null) {
                    createMcp2Obf.get().setSrg(createMcp2Srg.get().getSrg());
                    createMcp2Obf.get().dependsOn(createMcp2Srg);
                }

                if (createSrg2Mcp.get().getSrg() == null) {
                    createSrg2Mcp.get().setSrg(createMcp2Srg.get().getSrg());
                    createSrg2Mcp.get().dependsOn(createMcp2Srg);
                }
            }
            Project mcp = getMcpParent(project);
            if (mcp == null) {
                throw new IllegalStateException("Could not find MCP parent project, you must specify a parent chain to MCP.");
            }
            String mcp_version = mcp.getExtensions().findByType(MCPExtension.class).getConfig().get().getVersion();
            project.getDependencies().add(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME, "net.minecraft:client:" + mcp_version + ":extra"); //Needs to be client extra, to get the data files.
            project.getDependencies().add(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME, MCPRepo.getMappingDep(extension.getMappingChannel().get(), extension.getMappingVersion().get())); //Add mappings so that it can be used by reflection tools.

            dlMCMetaConfig.get().getMCVersion().convention(extension.getMCVersion());

            if (!extension.getAccessTransformers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcp.getTasks().getByName(MCPPlugin.SETUP_MCP_TASK_NAME);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createAT(mcp, new ArrayList<>(extension.getAccessTransformers().getFiles()), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "AccessTransformer", function);
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("ats/"));
                    userdevConfig.get().addAT(f);
                });
            }

            if (!extension.getSideAnnotationStrippers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcp.getTasks().getByName(MCPPlugin.SETUP_MCP_TASK_NAME);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createSAS(mcp, new ArrayList<>(extension.getSideAnnotationStrippers().getFiles()), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "SideStripper", function);
                extension.getSideAnnotationStrippers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("sas/"));
                    userdevConfig.get().addSAS(f);
                });
            }

            CreateFakeSASPatches fakePatches = null;
            PatcherExtension ext = extension;
            while (ext != null) {
                if (!ext.getSideAnnotationStrippers().isEmpty()) {
                    if (fakePatches == null)
                        fakePatches = project.getTasks().register(CREATE_FAKE_SAS_PATCHES_TASK_NAME, CreateFakeSASPatches.class).get();
                    ext.getSideAnnotationStrippers().forEach(fakePatches::addFile);
                }
                if (ext.getParent().isPresent())
                    ext = ext.getParent().get().getExtensions().findByType(PatcherExtension.class);
            }

            if (fakePatches != null) {
                for (TaskProvider<GenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                    task.get().dependsOn(fakePatches);
                    task.get().addPatchSet(fakePatches.getOutput());
                }
            }

            applyRangeConfig.get().setExcFiles(extension.getExcs().getFiles());
            applyRangeBaseConfig.get().setExcFiles(extension.getExcs().getFiles());

            if (!extension.getExtraMappings().isEmpty()) {
                extension.getExtraMappings().stream().filter(e -> e instanceof File).map(e -> (File) e).forEach(e -> {
                    userdevJar.get().from(e, c -> c.into("srgs/"));
                    userdevConfig.get().addSRG(e);
                });
                extension.getExtraMappings().stream().filter(e -> e instanceof String).map(e -> (String) e).forEach(e -> userdevConfig.get().addSRGLine(e));
            }

            //UserDev Config Default Values
            if (userdevConfig.get().getTool() == null) {
                userdevConfig.get().setTool("net.minecraftforge:binarypatcher:" + genJoinedBinPatches.get().getResolvedVersion() + ":fatjar");
                userdevConfig.get().setArguments("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");
            }
            if (userdevConfig.get().getUniversal() == null) {
                userdevConfig.get().setUniversal(project.getGroup().toString() + ':' + universalJar.get().getArchiveBaseName().getOrNull() + ':' + project.getVersion() + ':' + universalJar.get().getArchiveClassifier().getOrNull() + '@' + universalJar.get().getArchiveExtension().getOrNull());
            }
            if (userdevConfig.get().getSource() == null) {
                userdevConfig.get().setSource(project.getGroup().toString() + ':' + sourcesJar.get().getArchiveBaseName().getOrNull() + ':' + project.getVersion() + ':' + sourcesJar.get().getArchiveClassifier().getOrNull() + '@' + sourcesJar.get().getArchiveExtension().getOrNull());
            }
            if (!"a/".contentEquals(genPatches.get().getOriginalPrefix())) {
                userdevConfig.get().setPatchesOriginalPrefix(genPatches.get().getOriginalPrefix());
            }
            if (!"b/".contentEquals(genPatches.get().getModifiedPrefix())) {
                userdevConfig.get().setPatchesModifiedPrefix(genPatches.get().getModifiedPrefix());
            }
            if (procConfig != null) {
                userdevJar.get().dependsOn(procConfig);
                userdevConfig.get().setProcessor(extension.getProcessor());
                extension.getProcessorData().get().forEach((key, value) -> {
                    userdevJar.get().from(value, c -> c.into("processor/"));
                    userdevConfig.get().addProcessorData(key, value);
                });
            }
            userdevConfig.get().setNotchObf(extension.getNotchObf());

            //Allow generation of patches to skip S2S. For in-dev patches while the code doesn't compile.
            if (extension.isSrgPatches()) {
                genPatches.get().dependsOn(applyRangeBaseConfig);
                genPatches.get().setModified(applyRangeBaseConfig.get().getOutput());
            } else {
                //Remap the 'clean' with out mappings.
                ApplyMappings toMCPClean = project.getTasks().register(APPLY_MAPPINGS_CLEAN_TASK_NAME, ApplyMappings.class).get();
                toMCPClean.dependsOn(dlMappingsConfig, Lists.newArrayList(applyPatches.get().getDependsOn()));
                toMCPClean.getInput().set(applyPatches.get().getBase());
                toMCPClean.getMappings().set(dlMappingsConfig.get().getOutput());
                toMCPClean.getLambdas().set(false);

                //Zip up the current working folder as genPatches takes a zip
                Zip dirtyZip = project.getTasks().register(PATCHED_ZIP_TASK_NAME, Zip.class).get();
                dirtyZip.from(extension.getPatchedSrc());
                dirtyZip.getArchiveFileName().set("output.zip");
                dirtyZip.getDestinationDirectory().set(project.file("build/" + dirtyZip.getName() + "/"));

                //Fixup the inputs.
                applyPatches.get().setDependsOn(Lists.newArrayList(toMCPClean));
                applyPatches.get().setBase(toMCPClean.getOutput().get().getAsFile());
                genPatches.get().setDependsOn(Lists.newArrayList(toMCPClean, dirtyZip));
                genPatches.get().setBase(toMCPClean.getOutput().get().getAsFile());
                genPatches.get().setModified(dirtyZip.getArchivePath());
            }

            {
                String suffix = extension.getNotchObf() ? mcp_version.substring(0, mcp_version.lastIndexOf('-')) : mcp_version + ":srg";
                File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + suffix, true);
                File server = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + suffix, true);
                File joined = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + mcp_version + (extension.getNotchObf() ? "" : ":srg"), true);

                if (client == null || !client.exists())
                    throw new RuntimeException("Something horrible happenend, client " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");
                if (server == null || !server.exists())
                    throw new RuntimeException("Something horrible happenend, server " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");
                if (joined == null || !joined.exists())
                    throw new RuntimeException("Something horrible happenend, joined " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");

                TaskProvider<GenerateSRG> srg = extension.getNotchObf() ? createMcp2Obf : createMcp2Srg;
                reobfJar.get().dependsOn(srg);
                reobfJar.get().setSrg(srg.get().getOutput());
                //TODO: Extra SRGs, I don't think this is needed tho...

                genJoinedBinPatches.get().dependsOn(srg);
                genJoinedBinPatches.get().setSrg(srg.get().getOutput());
                genJoinedBinPatches.get().setCleanJar(joined);

                genClientBinPatches.get().dependsOn(srg);
                genClientBinPatches.get().setSrg(srg.get().getOutput());
                genClientBinPatches.get().setCleanJar(client);

                genServerBinPatches.get().dependsOn(srg);
                genServerBinPatches.get().setSrg(srg.get().getOutput());
                genServerBinPatches.get().setCleanJar(server);

                filterNew.get().dependsOn(srg);
                filterNew.get().setSrg(srg.get().getOutput());
                filterNew.get().addBlacklist(joined);
            }

            Map<String, String> tokens = new HashMap<>();

            try {
                // Check meta exists
                if (!dlMCMetaConfig.get().getOutput().get().getAsFile().exists()) {
                    // Force download meta
                    dlMCMetaConfig.get().downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(dlMCMetaConfig.get().getOutput().get().getAsFile(), VersionJson.class);

                tokens.put("asset_index", json.assetIndex.id);
            } catch (IOException e) {
                e.printStackTrace();

                // Fallback to MC version
                tokens.put("asset_index", extension.getMCVersion().get());
            }

            extension.getRuns().forEach(runConfig -> runConfig.tokens(tokens));
            Utils.createRunConfigTasks(extension, extractNatives.get(), downloadAssets.get(), createSrg2Mcp.get());
        });
    }


    private Project getMcpParent(Project project) {
        final PatcherExtension extension = project.getExtensions().findByType(PatcherExtension.class);
        if (extension == null || !extension.getParent().isPresent()) {
            return null;
        }
        Project parent = extension.getParent().get();
        MCPPlugin mcp = parent.getPlugins().findPlugin(MCPPlugin.class);
        PatcherPlugin patcher = parent.getPlugins().findPlugin(PatcherPlugin.class);
        if (mcp != null) {
            return parent;
        } else if (patcher != null) {
            return getMcpParent(parent);
        }
        return null;
    }

}
