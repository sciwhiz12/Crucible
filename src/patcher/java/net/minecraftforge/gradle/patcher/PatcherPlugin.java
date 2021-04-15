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
import net.minecraftforge.gradle.common.util.*;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionFactory;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.task.SetupMCP;
import net.minecraftforge.gradle.patcher.task.*;
import net.minecraftforge.gradle.mcp.task.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.common.task.ApplyMappings;
import net.minecraftforge.gradle.common.task.ApplyRangeMap;
import net.minecraftforge.gradle.common.task.ExtractExistingFiles;
import net.minecraftforge.gradle.common.task.ExtractRangeMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
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

        Configuration mcImplementation = project.getConfigurations().maybeCreate(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME);
        mcImplementation.setCanBeResolved(true);
        project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(mcImplementation);

        TaskProvider<Jar> jarConfig = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        TaskProvider<JavaCompile> javaCompile = project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class);
        SourceSet mainSource = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

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

        hideLicense.configure(task -> task.doLast(_task -> MojangLicenseHelper.hide(project, extension.getMappingChannel().get(), extension.getMappingVersion().get())));

        showLicense.configure(task -> task.doLast(_task -> MojangLicenseHelper.show(project, extension.getMappingChannel().get(), extension.getMappingVersion().get())));

        release.configure(task -> task.dependsOn(sourcesJar, universalJar, userdevJar));
        dlMappingsConfig.configure(task -> task.getMappings().set(extension.getMappings()));
        extractNatives.configure(task -> {
            task.getMeta().set(dlMCMetaConfig.flatMap(DownloadMCMeta::getOutput));
            task.getOutput().set(project.getLayout().getBuildDirectory().dir("natives"));
        });
        downloadAssets.configure(task -> task.getMeta().set(dlMCMetaConfig.flatMap(DownloadMCMeta::getOutput)));
        applyPatches.configure(task -> {
            task.getOutput().set(project.getLayout().getBuildDirectory().dir(task.getName()).map(d -> d.file("output.zip")));
            task.getRejects().set(project.getLayout().getBuildDirectory().dir(task.getName()).map(d -> d.file("rejects.zip").getAsFile()));
            task.getPatches().set(extension.getPatches());
            task.getPatchMode().set(PatchMode.ACCESS);
            if (project.hasProperty(UPDATING_PROPERTY)) {
                task.getPatchMode().set(PatchMode.FUZZY);
                task.getRejects().set(project.file("rejects/"));
                task.setFailOnError(false);
            }
        });
        toMCPConfig.configure(task -> {
            task.getInput().set(applyPatches.flatMap(ApplyPatches::getOutput));
            task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
            task.setLambdas(false);
        });
        extractMapped.configure(task -> {
            task.getZip().set(toMCPConfig.flatMap(ApplyMappings::getOutput));
            task.getOutput().set(extension.getPatchedSrc());
        });
        extractRangeConfig.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.getDependencies().from(jarConfig.flatMap(AbstractArchiveTask::getArchiveFile));

            // Only add main source, as we inject the patchedSrc into it as a sourceset.
            task.getSources().from(mainSource.getJava());
            task.getDependencies().from(javaCompile.map(JavaCompile::getClasspath));
        });
        createMcp2Srg.configure(task -> task.setReverse(true));
        createSrg2Mcp.configure(task -> task.setReverse(false));
        createMcp2Obf.configure(task -> {
            task.setNotch(true);
            task.setReverse(true);
        });
        createExc.configure(task -> task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput)));

        applyRangeConfig.configure(task -> {
            task.getSources().from(mainSource.getJava().minus(project.files(extension.getPatchedSrc())));
            task.getRangeMap().set(extractRangeConfig.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(GenerateSRG::getOutput));
            task.getExcFiles().from(createExc.flatMap(CreateExc::getOutput));
        });
        applyRangeBaseConfig.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.getSources().from(extension.getPatchedSrc());
            task.getRangeMap().set(extractRangeConfig.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(GenerateSRG::getOutput));
            task.getExcFiles().from(createExc.flatMap(CreateExc::getOutput));
        });
        genPatches.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.getOutput().set(extension.getPatches());
        });
        bakePatches.configure(task -> {
            task.dependsOn(genPatches);
            task.getInput().set(extension.getPatches());
            task.getOutput().set(new File(task.getTemporaryDir(), "output.zip"));
        });

        reobfJar.configure(task -> {
            task.getInput().set(jarConfig.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getClasspath().from(project.getConfigurations().getByName(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME));
        });
        genJoinedBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getPatchSets().from(extension.getPatches());
            task.getSide().set("joined");
        });
        genClientBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getPatchSets().from(extension.getPatches());
            task.getSide().set("client");
        });
        genServerBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getPatchSets().from(extension.getPatches());
            task.getSide().set("server");
        });
        genBinPatches.configure(task -> task.dependsOn(genJoinedBinPatches, genClientBinPatches, genServerBinPatches));
        filterNew.configure(task -> task.getInput().set(reobfJar.flatMap(ReobfuscateJar::getOutput)));
        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        sourcesJar.configure(task -> {
            task.from(project.zipTree(applyRangeConfig.flatMap(ApplyRangeMap::getOutput)));
            task.getArchiveClassifier().set("sources");
        });
        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        universalJar.configure(task -> {
            task.from(project.zipTree(filterNew.flatMap(FilterNewJar::getOutput)));
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
            task.dependsOn(sourcesJar);
            task.setOnlyIf(t -> extension.isSrgPatches());
            task.from(userdevConfig.flatMap(GenerateUserdevConfig::getOutput), e -> e.rename(f -> "config.json"));
            task.from(genJoinedBinPatches.flatMap(GenerateBinPatches::getOutput), e -> e.rename(f -> "joined.lzma"));
            task.from(project.zipTree(bakePatches.flatMap(BakePatches::getOutput)), e -> e.into("patches/"));
            task.getArchiveClassifier().set("userdev");
        });

        final boolean doingUpdate = project.hasProperty(UPDATE_MAPPINGS_PROPERTY);
        final String updateVersion = doingUpdate ? (String)project.property(UPDATE_MAPPINGS_PROPERTY) : null;
        final String updateChannel = doingUpdate
            ? (project.hasProperty(UPDATE_MAPPINGS_CHANNEL_PROPERTY) ? (String)project.property(UPDATE_MAPPINGS_CHANNEL_PROPERTY) : DEFAULT_UPDATE_MAPPINGS_CHANNEL)
            : null;
        if (doingUpdate) {
            TaskProvider<DownloadMCPMappings> dlMappingsNew = project.getTasks().register(DOWNLOAD_NEW_MAPPINGS_TASK_NAME, DownloadMCPMappings.class);
            dlMappingsNew.configure(task -> task.getMappings().set(updateChannel + updateVersion));

            TaskProvider<ApplyMappings> toMCPNew = project.getTasks().register(APPLY_NEW_MAPPINGS_TASK_NAME, ApplyMappings.class);
            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew);
                task.getInput().set(applyRangeConfig.flatMap(ApplyRangeMap::getOutput));
                task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
                task.setLambdas(false);
            });

            TaskProvider<ExtractExistingFiles> extractMappedNew = project.getTasks().register(EXTRACT_NEW_MAPPED_TASK_NAME, ExtractExistingFiles.class);
            extractMappedNew.configure(task -> task.getArchive().set(toMCPNew.flatMap(ApplyMappings::getOutput)));

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register(UPDATE_MAPPINGS_TASK_NAME, DefaultTask.class);
            updateMappings.configure(task -> task.dependsOn(extractMappedNew));
        }

        project.afterEvaluate(p -> {
            // Add the patched source as a source dir during afterEvaluate, to not be overwritten by buildscripts
            mainSource.java(v -> v.srcDir(extension.getPatchedSrc()));

            if (doingUpdate) {
                //Don't overwrite the patched code, re-setup the project.
                p.getTasks().named(EXTRACT_NEW_MAPPED_TASK_NAME, ExtractExistingFiles.class).configure(t -> t.getTargets().from(mainSource.getJava().minus(project.files(extension.getPatchedSrc()))));
            }

            //mainSource.resources(v -> {
            //}); //TODO: Asset downloading, needs asset index from json.
            //javaConv.getSourceSets().stream().forEach(s -> extractRangeConfig.get().addSources(s.getJava().getSrcDirs()));

            // Automatically create the patches folder if it does not exist
            if (extension.getPatches().isPresent()) {
                File patchesDir = extension.getPatches().get().getAsFile();
                if (!patchesDir.exists() && !patchesDir.mkdirs()) { // TODO: validate if we actually need to do this
                    p.getLogger().warn("Unable to create patches folder automatically, there may be some task errors");
                }
                sourcesJar.configure(t -> t.from(genPatches.flatMap(GeneratePatches::getOutput), e -> e.into("patches/")));
            }

            TaskProvider<DynamicJarExec> procConfig = extension.getProcessor() == null ? null : project.getTasks().register("postProcess", DynamicJarExec.class);

            if (extension.getParent().isPresent()) {
                Project parent = extension.getParent().get();
                TaskContainer parentTasks = parent.getTasks();
                MCPPlugin parentMCP = parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin parentPatcher = parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (parentMCP != null) {
                    MojangLicenseHelper.displayWarning(p, extension.getMappingChannel().get(), extension.getMappingVersion().get(), updateChannel, updateVersion);
                    TaskProvider<SetupMCP> setupMCPProvider = parentTasks.named(MCPPlugin.SETUP_MCP_TASK_NAME, SetupMCP.class);

                    Provider<RegularFile> output = setupMCPProvider.flatMap(SetupMCP::getOutput);

                    if (procConfig != null) {
                        procConfig.configure(t -> {
                            t.getInput().set(setupMCPProvider.flatMap(SetupMCP::getOutput));
                            t.getTool().set(extension.getProcessor().getVersion());
                            t.getArgs().set(extension.getProcessor().getArgs());
                            t.getData().set(extension.getProcessorData());
                        });
                        output = procConfig.flatMap(DynamicJarExec::getOutput);
                    }

                    extension.getCleanSrc().convention(output);
                    applyPatches.configure(t -> t.getBase().convention(extension.getCleanSrc().getAsFile()));
                    genPatches.configure(t -> t.getBase().convention(extension.getCleanSrc()));

                    TaskProvider<DownloadMCPConfig> downloadMCPConfig = parentTasks.named(MCPPlugin.DOWNLOAD_MCPCONFIG_TASK_NAME, DownloadMCPConfig.class);

                    TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register(EXTRACT_SRG_TASK_NAME, ExtractMCPData.class);
                    extractSrg.configure(t -> t.getConfig().set(downloadMCPConfig.flatMap(DownloadMCPConfig::getOutput)));
                    createMcp2Srg.configure(t -> t.getSrg().convention(extractSrg.flatMap(ExtractMCPData::getOutput)));

                    TaskProvider<ExtractMCPData> extractStatic = project.getTasks().register(EXTRACT_STATIC_TASK_NAME, ExtractMCPData.class);
                    extractStatic.configure(t -> {
                        t.getConfig().set(downloadMCPConfig.flatMap(DownloadMCPConfig::getOutput));
                        t.getKey().set("statics");
                        t.setAllowEmpty(true);
                        t.getOutput().set(project.getLayout().getBuildDirectory().dir(t.getName()).map(d -> d.file("output.txt")));
                    });

                    TaskProvider<ExtractMCPData> extractConstructors = project.getTasks().register(EXTRACT_CONSTRUCTORS_TASK_NAME, ExtractMCPData.class);
                    extractConstructors.configure(t -> {
                        t.getConfig().set(downloadMCPConfig.flatMap(DownloadMCPConfig::getOutput));
                        t.getKey().set("constructors");
                        t.setAllowEmpty(true);
                        t.getOutput().set(project.getLayout().getBuildDirectory().dir(t.getName()).map(d -> d.file("output.txt")));
                    });

                    createExc.configure(t -> {
                        t.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getOutput));
                        t.getStatics().convention(extractStatic.flatMap(ExtractMCPData::getOutput));
                        t.getConstructors().convention(extractConstructors.flatMap(ExtractMCPData::getOutput));
                    });
                } else if (parentPatcher != null) {
                    PatcherExtension pExt = parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    TaskProvider<ApplyPatches> parentApply = parentTasks.named(APPLY_PATCHES_TASK_NAME, ApplyPatches.class);

                    Provider<RegularFile> output = parentApply.flatMap(ApplyPatches::getOutput);
                    if (procConfig != null) {
                        procConfig.configure(t -> {
                            t.getInput().set(parentApply.flatMap(ApplyPatches::getOutput));
                            t.getTool().set(extension.getProcessor().getVersion());
                            t.getArgs().set(extension.getProcessor().getArgs());
                            t.getData().set(extension.getProcessorData());
                        });
                        output = procConfig.flatMap(DynamicJarExec::getOutput);
                    }

                    extension.getCleanSrc().convention(output);
                    applyPatches.get().getBase().convention(extension.getCleanSrc().getAsFile());
                    genPatches.get().getBase().convention(extension.getCleanSrc());

                    TaskProvider<GenerateSRG> parentMcp2Srg = parentTasks.named(CREATE_MCP_TO_SRG_TASK_NAME, GenerateSRG.class);
                    createMcp2Srg.configure(t -> t.getSrg().convention(parentMcp2Srg.flatMap(GenerateSRG::getSrg)));

                    TaskProvider<CreateExc> parentCreateExc = parentTasks.named(CREATE_EXC_TASK_NAME, CreateExc.class);
                    createExc.configure(t -> {
                        t.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getOutput));
                        t.getStatics().convention(parentCreateExc.flatMap(CreateExc::getStatics));
                        t.getConstructors().convention(parentCreateExc.flatMap(CreateExc::getConstructors));
                    });

                    TaskProvider<GenerateBinPatches> parentJoinedBinPatches = parentTasks.named(GENERATE_JOINED_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
                    genJoinedBinPatches.configure(t -> t.getPatchSets().from(parentJoinedBinPatches.map(GenerateBinPatches::getPatchSets)));

                    TaskProvider<GenerateBinPatches> parentClientBinPatches = parentTasks.named(GENERATE_CLIENT_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
                    genClientBinPatches.configure(t -> t.getPatchSets().from(parentClientBinPatches.map(GenerateBinPatches::getPatchSets)));

                    TaskProvider<GenerateBinPatches> parentServerBinPatches = parentTasks.named(GENERATE_SERVER_BIN_PATCHES_TASK_NAME, GenerateBinPatches.class);
                    genServerBinPatches.configure(t -> t.getPatchSets().from(parentServerBinPatches.map(GenerateBinPatches::getPatchSets)));

                    TaskProvider<Jar> parentJar = parentTasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class);
                    filterNew.configure(t -> t.getBlacklist().from(parentJar.flatMap(AbstractArchiveTask::getArchiveFile)));
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }

                dlMappingsConfig.configure(t -> t.getMappings().convention(extension.getMappings()));

                createMcp2Srg.configure(t -> t.getMappings().convention(dlMappingsConfig.flatMap(DownloadMCPMappings::getMappings)));
                createMcp2Obf.configure(t -> {
                    t.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getOutput));
                    t.getMappings().convention(dlMappingsConfig.flatMap(DownloadMCPMappings::getMappings));
                });
                createSrg2Mcp.configure(t -> {
                    t.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getOutput));
                    t.getMappings().convention(dlMappingsConfig.flatMap(DownloadMCPMappings::getMappings));
                });
            }
            final Project mcpParentProject = getMcpParent(project);
            if (mcpParentProject == null) {
                throw new IllegalStateException("Could not find MCP parent project, you must specify a parent chain to MCP.");
            }
            Provider<String> mcpConfigVersion = mcpParentProject.getExtensions().findByType(MCPExtension.class).getConfig().map(Artifact::getVersion);
            // Needs to be client extra, to get the data files.
            project.getDependencies().add(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME, mcpConfigVersion.map(ver -> "net.minecraft:client:" + ver + ":extra"));
            // Add mappings so that it can be used by reflection tools.
            project.getDependencies().add(MINECRAFT_IMPLEMENTATION_CONFIGURATION_NAME, extension.getMappingChannel().zip(extension.getMappingVersion(), MCPRepo::getMappingDep));

            dlMCMetaConfig.configure(t -> t.getMCVersion().convention(extension.getMinecraftVersion()));
            dlMCMetaConfig.get().getMCVersion().convention(extension.getMinecraftVersion());

            if (!extension.getAccessTransformers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcpParentProject.getTasks().getByName(MCPPlugin.SETUP_MCP_TASK_NAME);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createAT(mcpParentProject, new ArrayList<>(extension.getAccessTransformers().getFiles()), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "AccessTransformer", function);
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("ats/"));
                    userdevConfig.get().getATs().from(f);
                });
            }

            if (!extension.getSideAnnotationStrippers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcpParentProject.getTasks().getByName(MCPPlugin.SETUP_MCP_TASK_NAME);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createSAS(mcpParentProject, new ArrayList<>(extension.getSideAnnotationStrippers().getFiles()), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "SideStripper", function);
                extension.getSideAnnotationStrippers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("sas/"));
                    userdevConfig.get().getSASs().from(f);
                });
            }

            TaskProvider<CreateFakeSASPatches> fakePatches = null;
            PatcherExtension ext = extension;
            while (ext != null) {
                if (!ext.getSideAnnotationStrippers().isEmpty()) {
                    if (fakePatches == null)
                        fakePatches = project.getTasks().register(CREATE_FAKE_SAS_PATCHES_TASK_NAME, CreateFakeSASPatches.class);
                    PatcherExtension finalExt = ext;
                    fakePatches.configure(t -> t.getFiles().from(finalExt.getSideAnnotationStrippers()));
                }
                if (ext.getParent().isPresent())
                    ext = ext.getParent().get().getExtensions().findByType(PatcherExtension.class);
            }

            if (fakePatches != null) {
                for (TaskProvider<GenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                    TaskProvider<CreateFakeSASPatches> finalFakePatches = fakePatches;
                    task.configure(t -> t.getPatchSets().from(finalFakePatches.flatMap(CreateFakeSASPatches::getOutput)));
                }
            }

            applyRangeConfig.configure(t -> t.getExcFiles().from(extension.getExcs()));
            applyRangeBaseConfig.configure(t -> t.getExcFiles().from(extension.getExcs()));

            if (!extension.getExtraMappings().isEmpty()) {
                extension.getExtraMappings().stream().filter(e -> e instanceof File).map(e -> (File) e).forEach(e -> {
                    userdevJar.get().from(e, c -> c.into("srgs/"));
                    userdevConfig.get().getSRGs().from(e);
                });
                extension.getExtraMappings().stream().filter(e -> e instanceof String).map(e -> (String) e).forEach(e -> userdevConfig.get().getSRGLines().add(e));
            }

            //UserDev Config Default Values
            userdevConfig.configure(devTask -> {
                devTask.getTool().convention(genJoinedBinPatches.map(s -> "net.minecraftforge:binarypatcher:" + s.getResolvedVersion() + ":fatjar"));
                devTask.getArguments().convention(Arrays.asList("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}"));
                devTask.getUniversal().convention(
                        universalJar.flatMap(t -> t.getArchiveBaseName().flatMap(baseName -> t.getArchiveClassifier().flatMap(classifier -> t.getArchiveExtension().map(jarExt ->
                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                        )))));
                devTask.getSource().convention(
                        sourcesJar.flatMap(t -> t.getArchiveBaseName().flatMap(baseName -> t.getArchiveClassifier().flatMap(classifier -> t.getArchiveExtension().map(jarExt ->
                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                        )))));
                devTask.getPatchesOriginalPrefix().convention(genPatches.flatMap(GeneratePatches::getOriginalPrefix));
                devTask.getPatchesModifiedPrefix().convention(genPatches.flatMap(GeneratePatches::getModifiedPrefix));
                devTask.setNotchObf(extension.getNotchObf());
            });

            if (procConfig != null) {
                userdevJar.get().dependsOn(procConfig);
                if (extension.getProcessor() != null) {
                    userdevConfig.get().setProcessor(extension.getProcessor());
                }
                extension.getProcessorData().get().forEach((key, value) -> {
                    userdevJar.get().from(value, c -> c.into("processor/"));
                    userdevConfig.get().addProcessorData(key, value);
                });
            }

            // Allow generation of patches to skip S2S. For in-dev patches while the code doesn't compile.
            if (extension.isSrgPatches()) {
                genPatches.configure(t -> t.getModified().set(applyRangeBaseConfig.flatMap(ApplyRangeMap::getOutput)));
            } else {
                // Remap the 'clean' with out mappings.
                TaskProvider<ApplyMappings> toMCPClean = project.getTasks().register(APPLY_MAPPINGS_CLEAN_TASK_NAME, ApplyMappings.class);
                toMCPClean.configure(t -> {
                    t.getInput().set(applyPatches.flatMap(ApplyPatches::getOutput));
                    t.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
                    t.setLambdas(false);
                });

                // Zip up the current working folder as genPatches takes a zip
                TaskProvider<Zip> dirtyZip = project.getTasks().register(PATCHED_ZIP_TASK_NAME, Zip.class);
                dirtyZip.configure(t -> {
                    t.from(extension.getPatchedSrc());
                    t.getArchiveFileName().set("output.zip");
                    t.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir(t.getName()));
                });

                // Fixup the inputs.
                applyPatches.configure(t -> t.getBase().set(toMCPClean.flatMap(clean -> clean.getOutput().getAsFile())));
                genPatches.configure(t -> {
                    t.getBase().set(toMCPClean.flatMap(ApplyMappings::getOutput));
                    t.getModified().set(dirtyZip.flatMap(AbstractArchiveTask::getArchiveFile));
                });
            }

            {
                Provider<String> version = mcpConfigVersion.map(ver -> extension.getNotchObf() ? ver.substring(0, ver.lastIndexOf('-')) : ver);
                Provider<String> classifier = project.provider(() -> extension.getNotchObf() ? "" : ":srg");

                Provider<File> clientJar = version.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Client " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });
                Provider<File> serverJar = version.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Server " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });
                Provider<File> joinedJar = mcpConfigVersion.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Joined " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });

                TaskProvider<GenerateSRG> srg = extension.getNotchObf() ? createMcp2Obf : createMcp2Srg;
                reobfJar.configure(t -> t.getSrg().set(srg.flatMap(GenerateSRG::getOutput)));
                //TODO: Extra SRGs, I don't think this is needed tho...

                genJoinedBinPatches.configure(t -> {
                    t.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                    t.getCleanJar().fileProvider(joinedJar);
                });

                genClientBinPatches.configure(t -> {
                    t.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                    t.getCleanJar().fileProvider(clientJar);
                });

                genServerBinPatches.configure(t -> {
                    t.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                    t.getCleanJar().fileProvider(serverJar);
                });

                filterNew.configure(t -> {
                    t.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                    t.getBlacklist().from(joinedJar);
                });
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
                tokens.put("asset_index", extension.getMinecraftVersion().get());
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
