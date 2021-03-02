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

package net.minecraftforge.gradle.userdev;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import net.minecraftforge.gradle.common.FGBasePlugin;
import net.minecraftforge.gradle.common.task.ApplyMappings;
import net.minecraftforge.gradle.common.task.ApplyRangeMap;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DownloadMCMeta;
import net.minecraftforge.gradle.common.task.DownloadMavenArtifact;
import net.minecraftforge.gradle.common.task.ExtractExistingFiles;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.task.ExtractRangeMap;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.task.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.userdev.task.RenameJarInPlace;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingRepo;
import net.minecraftforge.gradle.userdev.util.Deobfuscator;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import net.minecraftforge.srgutils.IMappingFile;

public class UserDevPlugin implements Plugin<Project> {
    public static final String MINECRAFT_CONFIGURATION_NAME = "minecraft";
    public static final String OBFUSCATED_CONFIGURATION_NAME = "__obfuscated";

    public static final String MC_VERSION_EXT_PROPERTY = "MC_VERSION";
    public static final String MCP_VERSION_EXT_PROPERTY = "MCP_VERSION";

    public static final String UPDATE_MAPPINGS_VERSION_PROPERTY = "UPDATE_MAPPINGS";
    public static final String UPDATE_MAPPINGS_CHANNEL_PROPERTY = "UPDATE_MAPPINGS_CHANNEL";
    private static final String DEFAULT_UPDATE_MAPPINGS_CHANNEL = "snapshot";

    public static final String REOBF_EXTENSION_NAME = "reobf";

    // Normal userdev tasks
    public static final String DOWNLOAD_MCPCONFIG_TASK_NAME = "downloadMcpConfig";
    public static final String EXTRACT_SRG_TASK_NAME = "extractSrg";
    public static final String CREATE_SRG_TO_MCP_TASK_NAME = "createSrgToMcp";
    public static final String CREATE_MCP_TO_SRG_TASK_NAME = "createMcpToSrg";
    public static final String DOWNLOAD_MC_META_TASK_NAME = "downloadMCMeta";
    public static final String EXTRACT_NATIVES_TASK_NAME = "extractNatives";
    public static final String DOWNLOAD_ASSETS_TASK_NAME = "downloadAssets";

    // updateMappings tasks
    public static final String DOWNLOAD_NEW_MAPPINGS_TASK_NAME = "downloadMappingsNew";
    public static final String EXTRACT_RANGE_MAP_TASK_NAME = "extractRangeMap";
    public static final String APPLY_RANGE_MAP_TASK_NAME = "applyRangeMap";
    public static final String APPLY_MAPPINGS_TASK_NAME = "srg2mcpNew";
    public static final String EXTRACT_MAPPED_TASK_NAME = "extractMappedNew";
    public static final String UPDATE_MAPPINGS_TASK_NAME = "updateMappings";

    @Override
    public void apply(@Nonnull Project project) {
        project.getPlugins().apply(FGBasePlugin.class);

        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create(UserDevExtension.EXTENSION_NAME, UserDevExtension.class, project);

        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File nativesFolder = project.file("build/natives/");

        NamedDomainObjectContainer<RenameJarInPlace> reobf = createReobfExtension(project);

        Configuration minecraft = project.getConfigurations().maybeCreate(MINECRAFT_CONFIGURATION_NAME);
        for (String cfg : new String[]{JavaPlugin.COMPILE_CONFIGURATION_NAME, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME}) {
            Configuration c = project.getConfigurations().findByName(cfg);
            if (c != null)
                c.extendsFrom(minecraft);
        }

        //Let gradle handle the downloading by giving it a configuration to dl. We'll focus on applying mappings to it.
        Configuration internalObfConfiguration = project.getConfigurations().maybeCreate(OBFUSCATED_CONFIGURATION_NAME);
        internalObfConfiguration.setDescription("Generated scope for obfuscated dependencies");

        //create extension for dependency remapping
        //can't create at top-level or put in `minecraft` ext due to configuration name conflict
        Deobfuscator deobfuscator = new Deobfuscator(project, Utils.getCache(project, "deobf_dependencies"));
        DependencyRemapper remapper = new DependencyRemapper(project, deobfuscator);
        project.getExtensions().create(DependencyManagementExtension.EXTENSION_NAME, DependencyManagementExtension.class, project, remapper);

        TaskProvider<DownloadMavenArtifact> downloadMcpConfig = project.getTasks().register(DOWNLOAD_MCPCONFIG_TASK_NAME, DownloadMavenArtifact.class);
        TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register(EXTRACT_SRG_TASK_NAME, ExtractMCPData.class);
        TaskProvider<GenerateSRG> createSrgToMcp = project.getTasks().register(CREATE_SRG_TO_MCP_TASK_NAME, GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcpToSrg = project.getTasks().register(CREATE_MCP_TO_SRG_TASK_NAME, GenerateSRG.class);
        TaskProvider<DownloadMCMeta> downloadMCMeta = project.getTasks().register(DOWNLOAD_MC_META_TASK_NAME, DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register(EXTRACT_NATIVES_TASK_NAME, ExtractNatives.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register(DOWNLOAD_ASSETS_TASK_NAME, DownloadAssets.class);
        TaskProvider<DefaultTask> hideLicense = project.getTasks().register(MojangLicenseHelper.HIDE_LICENSE_TASK_NAME, DefaultTask.class);
        TaskProvider<DefaultTask> showLicense = project.getTasks().register(MojangLicenseHelper.SHOW_LICENSE_TASK_NAME, DefaultTask.class);

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

        extractSrg.configure(task -> {
            task.dependsOn(downloadMcpConfig);
            task.setConfig(() -> downloadMcpConfig.get().getOutput());
        });

        createSrgToMcp.configure(task -> {
            task.setReverse(false);
            task.dependsOn(extractSrg);
            task.setSrg(extractSrg.get().getOutput());
            task.setMappings(extension.getMappings().get());
            task.setFormat(IMappingFile.Format.SRG);
            task.setOutput(project.file("build/" + createSrgToMcp.getName() + "/output.srg"));
        });

        createMcpToSrg.configure(task -> {
            task.setReverse(true);
            task.dependsOn(extractSrg);
            task.setSrg(extractSrg.get().getOutput());
            task.setMappings(extension.getMappings().get());
        });

        extractNatives.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
            task.setOutput(nativesFolder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
        });

        final boolean doingUpdate = project.hasProperty(UPDATE_MAPPINGS_VERSION_PROPERTY);
        final String updateVersion = doingUpdate ? (String)project.property(UPDATE_MAPPINGS_VERSION_PROPERTY) : null;
        final String updateChannel = doingUpdate
            ? (project.hasProperty(UPDATE_MAPPINGS_CHANNEL_PROPERTY) ? (String)project.property(UPDATE_MAPPINGS_CHANNEL_PROPERTY) : DEFAULT_UPDATE_MAPPINGS_CHANNEL)
            : null;
        if (doingUpdate) {
            logger.lifecycle("This process uses Srg2Source for java source file renaming. Please forward relevant bug reports to https://github.com/MinecraftForge/Srg2Source/issues.");

            JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            Set<File> srcDirs = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava().getSrcDirs();

            TaskProvider<DownloadMCPMappings> dlMappingsNew = project.getTasks().register(DOWNLOAD_NEW_MAPPINGS_TASK_NAME, DownloadMCPMappings.class);
            TaskProvider<ExtractRangeMap> extractRangeConfig = project.getTasks().register(EXTRACT_RANGE_MAP_TASK_NAME, ExtractRangeMap.class);
            TaskProvider<ApplyRangeMap> applyRangeConfig = project.getTasks().register(APPLY_RANGE_MAP_TASK_NAME, ApplyRangeMap.class);
            TaskProvider<ApplyMappings> toMCPNew = project.getTasks().register(APPLY_MAPPINGS_TASK_NAME, ApplyMappings.class);
            TaskProvider<ExtractExistingFiles> extractMappedNew = project.getTasks().register(EXTRACT_MAPPED_TASK_NAME, ExtractExistingFiles.class);

            extractRangeConfig.configure(task -> {
                task.addSources(srcDirs);
                task.addDependencies(javaCompile.getClasspath());
            });

            applyRangeConfig.configure(task -> {
                task.dependsOn(extractRangeConfig, createMcpToSrg);
                task.setRangeMap(extractRangeConfig.get().getOutput());
                task.setSrgFiles(createMcpToSrg.get().getOutput());
                task.setSources(srcDirs);
            });

            dlMappingsNew.configure(task -> {
                task.setMappings(updateChannel + "_" + updateVersion);
                task.setOutput(project.file("build/mappings_new.zip"));
            });

            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew, applyRangeConfig);
                task.setInput(applyRangeConfig.get().getOutput());
                task.setMappings(dlMappingsNew.get().getOutput());
            });

            extractMappedNew.configure(task -> {
                task.dependsOn(toMCPNew);
                task.setArchive(toMCPNew.get().getOutput());
                srcDirs.forEach(task::addTarget);
            });

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register(UPDATE_MAPPINGS_TASK_NAME, DefaultTask.class);
            updateMappings.get().dependsOn(extractMappedNew);
        }

        project.afterEvaluate(p -> {
            MinecraftUserRepo mcrepo = null;
            DeobfuscatingRepo deobfrepo = null;

            DependencySet deps = minecraft.getDependencies();
            for (Dependency dep : new ArrayList<>(deps)) {
                if (!(dep instanceof ExternalModuleDependency))
                    throw new IllegalArgumentException("minecraft dependency must be a maven dependency.");
                if (mcrepo != null)
                    throw new IllegalArgumentException("Only allows one minecraft dependency.");
                deps.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), new ArrayList<>(extension.getAccessTransformers().getFiles()), extension.getMappings().get());
                String newDep = mcrepo.getDependencyString();
                //p.getLogger().lifecycle("New Dep: " + newDep);
                ExternalModuleDependency ext = (ExternalModuleDependency) p.getDependencies().create(newDep);
                {
                    if (MinecraftUserRepo.CHANGING_USERDEV)
                        ext.setChanging(true);
                    minecraft.resolutionStrategy(strat -> {
                        strat.cacheChangingModulesFor(10, TimeUnit.SECONDS);
                    });
                }
                minecraft.getDependencies().add(ext);
            }

            if (!internalObfConfiguration.getDependencies().isEmpty()) {
                deobfrepo = new DeobfuscatingRepo(project, internalObfConfiguration, deobfuscator);
                if (deobfrepo.getResolvedOrigin() == null) {
                    project.getLogger().error("DeobfRepo attempted to resolve an origin repo early but failed, this may cause issues with some IDEs");
                }
            }
            remapper.attachMappings(extension.getMappings().get());

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                    .add(mcrepo)
                    .add(deobfrepo)
                    .add(MCPRepo.create(project))
                    .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                    .attach(project);

            MojangLicenseHelper.displayWarning(p, extension.getMappingChannel().get(), extension.getMappingVersion().get(), updateChannel, updateVersion);

            if (mcrepo == null)
                throw new IllegalStateException("Missing 'minecraft' dependency entry.");
            mcrepo.validate(minecraft, extension.getRuns().getAsMap(), extractNatives.get(), downloadAssets.get(), createSrgToMcp.get()); //This will set the MC_VERSION property.

            String mcVer = (String) project.getExtensions().getExtraProperties().get(MC_VERSION_EXT_PROPERTY);
            String mcpVer = (String) project.getExtensions().getExtraProperties().get(MCP_VERSION_EXT_PROPERTY);
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().setMCVersion(mcVer);

            RenameJarInPlace reobfJar = reobf.create(JavaPlugin.JAR_TASK_NAME);
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.setMappings(createMcpToSrg.get().getOutput());

            String assetIndex = mcVer;

            try {
                // Check meta exists
                if (!downloadMCMeta.get().getOutput().exists()) {
                    // Force download meta
                    downloadMCMeta.get().downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(downloadMCMeta.get().getOutput(), VersionJson.class);

                assetIndex = json.assetIndex.id;
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Finalize asset index
            final String finalAssetIndex = assetIndex;

            extension.getRuns().forEach(runConfig -> runConfig.token("asset_index", finalAssetIndex));
            Utils.createRunConfigTasks(extension, extractNatives.get(), downloadAssets.get(), createSrgToMcp.get());
        });
    }

    private NamedDomainObjectContainer<RenameJarInPlace> createReobfExtension(Project project) {
        NamedDomainObjectContainer<RenameJarInPlace> reobf = project.container(RenameJarInPlace.class, jarName -> {
            String name = Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);
            JavaPluginConvention java = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);
            task.setClasspath(java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getCompileClasspath());

            final Task createMcpToSrg = project.getTasks().findByName(CREATE_MCP_TO_SRG_TASK_NAME);
            if (createMcpToSrg != null) {
                task.setMappings(() -> createMcpToSrg.getOutputs().getFiles().getSingleFile());
            }

            project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(task);

            // do after-Evaluate resolution, for the same of good error reporting
            project.afterEvaluate(p -> {
                Task jar = project.getTasks().getByName(jarName);
                if (!(jar instanceof Jar))
                    throw new IllegalStateException(jarName + "  is not a jar task. Can only reobf jars!");
                task.setInput(((Jar) jar).getArchiveFile().get().getAsFile());
                task.dependsOn(jar);

                if (createMcpToSrg != null && task.getMappings().equals(createMcpToSrg.getOutputs().getFiles().getSingleFile())) {
                    task.dependsOn(createMcpToSrg); // Add needed dependency if uses default mappings
                }
            });

            return task;
        });
        project.getExtensions().add(REOBF_EXTENSION_NAME, reobf);
        return reobf;
    }

}
