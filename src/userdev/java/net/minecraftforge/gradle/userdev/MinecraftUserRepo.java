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

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.repository.Repository;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.config.UserdevConfigV2;
import net.minecraftforge.gradle.common.config.UserdevConfigV2.DataFunction;
import net.minecraftforge.gradle.common.task.ApplyBinPatches;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DynamicJarExec;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionFactory;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import net.minecraftforge.gradle.userdev.task.AccessTransformJar;
import net.minecraftforge.gradle.userdev.task.ApplyMCPFunction;
import net.minecraftforge.gradle.userdev.task.HackyJavaCompile;
import net.minecraftforge.gradle.userdev.task.RenameJar;
import net.minecraftforge.gradle.userdev.task.RenameJarInPlace;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.MinecraftVersion;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IRenamer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginConvention;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MinecraftUserRepo extends BaseRepo {
    public static final boolean CHANGING_USERDEV = false; //Used when testing to update the userdev cache every 30 seconds.
    private static final MinecraftVersion v1_13 = MinecraftVersion.from("1.13");
    private static final String MCI_JAR_TASK_PREFIX = "mciJar";
    private static final String ACCESS_TRANSFORM_JAR_TASK_PREFIX = "atJar";
    private static final String RENAME_JAR_IN_PLACE_TASK_PREFIX = "renameJarInPlace";
    private static final String RENAME_JAR = "renameJar";
    private static final String RENAME_JAR_TASK_PREFIX = "renameJar";
    private static final String APPLY_BINPATCHES_TASK_PREFIX = "applyBinpatches";
    private static final String POST_PROCESS_TASK_PREFIX = "postProcess";
    private static final String COMPILE_JAVA_TASK_PREFIX = "compileJava";
    private final Project project;
    private final String GROUP;
    private final String NAME;
    private final String VERSION;
    private final List<File> ATS;
    private final String AT_HASH;
    private final String MAPPING;
    private final boolean isPatcher;
    private final Map<String, McpNames> mapCache = new HashMap<>();
    private boolean loadedParents = false;
    private Patcher parent;
    private MCP mcp;
    @SuppressWarnings("unused")
    private Repository repo;
    private Set<File> extraDataFiles;

    /* TODO:
     * Steps to produce each dep:
     *
     * src:
     *   decompile using MCPConfig with all AT's applied (Already set this up in MCP.getSrcRuntime)
     *   for each parent:
     *     Apply patches
     *     Inject source
     *   remap source
     *   recompile
     *
     *
     * Natives:
     *   Extract natives to a central folder
     *
     * Start:
     *   Build general GradleStart that injects parent access transformers and natives folder.
     *
     * Version Setup:
     *   [Version]_mapped_[mapping]_at_[AtHash]
     */
    public MinecraftUserRepo(Project project, String group, String name, String version, List<File> ats, String mapping) {
        super(Utils.getCache(project, "minecraft_user_repo"), project.getLogger());
        this.project = project;
        this.GROUP = group;
        this.NAME = name;
        this.VERSION = version;
        this.ATS = ats.stream().filter(File::exists).collect(Collectors.toList());
        try {
            this.AT_HASH = ATS.isEmpty() ? null : HashFunction.SHA1.hash(ATS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.MAPPING = mapping;
        this.isPatcher = !"net.minecraft".equals(GROUP);

        repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .filter(ArtifactIdentifier.groupEquals(GROUP))
            .filter(ArtifactIdentifier.nameEquals(NAME))
            .provide(this)
        );
    }

    @Override
    protected File getCacheRoot() {
        if (this.AT_HASH == null)
            return super.getCacheRoot();
        return project.file("build/fg_cache/");
    }

    public void validate(Configuration cfg, Map<String, RunConfig> runs, ExtractNatives extractNatives, DownloadAssets downloadAssets, GenerateSRG createSrgToMcp) {
        getParents();
        if (mcp == null)
            throw new IllegalStateException("Invalid minecraft dependency: " + GROUP + ":" + NAME + ":" + VERSION);
        ExtraPropertiesExtension ext = project.getExtensions().getExtraProperties();
        ext.set("MC_VERSION", mcp.getMCVersion());
        ext.set("MCP_VERSION", mcp.getArtifact().getVersion());

        //Maven POMs can't self-reference apparently, so we have to add any deps that are self referential.
        Patcher patcher = parent;
        while (patcher != null) {
            patcher.getLibraries().stream().map(Artifact::from)
            .filter(e -> GROUP.equals(e.getGroup()) && NAME.equals(e.getName()))
            .forEach(e -> {
                String dep = getDependencyString();
                if (e.getClassifier() != null)
                {
                    dep += ":" + e.getClassifier();
                    if (e.getClassifier().indexOf('.') != -1)
                        throw new IllegalArgumentException("Can not set Minecraft dependency with classifier containing '.'");
                }
                if (e.getExtension() != null && !"jar".equals(e.getExtension()))
                    dep += "@" + e.getExtension();

                debug("    New Self Dep: " + dep);
                ExternalModuleDependency _dep = (ExternalModuleDependency)project.getDependencies().create(dep);
                if (CHANGING_USERDEV) {
                    _dep.setChanging(true);
                }
                cfg.getDependencies().add(_dep);
            });
            patcher = patcher.getParent();
        }

        Map<String, String> tokens = new HashMap<>();
        tokens.put("assets_root", downloadAssets.getOutput().getAbsolutePath());
        tokens.put("natives", extractNatives.getOutput().get().getAsFile().getAbsolutePath());
        tokens.put("mc_version", mcp.getMCVersion());
        tokens.put("mcp_version", mcp.getArtifact().getVersion());
        tokens.put("mcp_mappings", MAPPING);
        tokens.put("mcp_to_srg", createSrgToMcp.getOutput().get().getAsFile().getAbsolutePath());

        if (parent != null && parent.getConfig().runs != null) {
            parent.getConfig().runs.forEach((name, dev) -> {
                final RunConfig run = runs.get(name);
                if (run != null)
                    run.parent(0, dev);
            });
        }

        runs.forEach((name, run) -> run.tokens(tokens));

        this.extraDataFiles = this.buildExtraDataFiles();
    }

    /**
     * Previously, Configuration.resolve() was called indirectly from
     * BaseRepo.getArtifact(). Due to the extensive amount of Gradle code that
     * runs as a result of the resolve() call, deadlock could occur as follows:
     *
     * 1. Thread #1: During resolution of a dependency, Gradle calls
     * BaseRepo#getArtifact(). The 'synchronized' block is entering,
     * causing a lock to be taken on the artifact name
     *
     * 2. Thread #2: On a different thread, internal Gradle code takes a lock
     * in the class org.gradle.internal.event.DefaultListenerManager.EventBroadcast.ListenerDispatch
     *
     * 3. Thread #1: Execution continues on the 'BaseRepo#getArtifact()' call
     * stack, reaching the call to Configuration.resolve(). This call leads
     * to Gradle internally dispatching events through the same class
     * org.gradle.internal.event.DefaultListenerManager.EventBroadcast.ListenerDispatch.
     * This thread is now blocked on the internal Gradle lock taken by Thread #2
     *
     * 4. Thread #2: Execution continues, and attempts to resolve the same
     * dependency that Thread #1 is currently resolving. Since Thread #1 is
     * still in the 'synchronized' block with the same artifact name, Thread #2
     * blocks.
     *
     * These threads are now deadlocked: Thread #1 is holding the
     * BaseRepo#getArtifact lock while waiting on an internal Gradle lock,
     * while Thread #2 is holding the same internal Gradle lock while waiting
     * on the BaseRepo#getArtifact lock.
     *
     * Visit https://git.io/fhHLk to see a stack dump showing this deadlock
     *
     * Fortunately, the solution is fairly simply. We can move the entire
     * dependency creation/resolution block to an earlier point in
     * ForgeGradle's execution. Since the client 'data'/'extra'/libraries only
     * depend on the MCP config file, we can do this during plugin
     * initialization.
     *
     * This has the added benefit of speeding up ForgeGradle - this block of
     * code will only be executed once, instead of during every call to
     * compileJava
     * @return
     */
    private Set<File> buildExtraDataFiles() {
        Configuration cfg = project.getConfigurations().create(getNextTaskName("compileJava"));
        List<String> deps = new ArrayList<>();
        deps.add("net.minecraft:client:" + mcp.getMCVersion() + ":extra");
        deps.addAll(mcp.getLibraries());
        Patcher patcher = parent;
        while (patcher != null) {
            deps.addAll(patcher.getLibraries());
            patcher = patcher.getParent();
        }
        deps.forEach(dep -> cfg.getDependencies().add(project.getDependencies().create(dep)));
        return cfg.resolve();
    }

    @SuppressWarnings("unused")
    private File cacheRaw(String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '.' + ext);
    }
    private File cacheRaw(String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '-' + classifier + '.' + ext);
    }
    private File cacheMapped(String mapping, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '.' + ext);
    }
    private File cacheMapped(String mapping, String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '-' + classifier + '.' + ext);
    }
    private File cacheAT(String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersionAT(), NAME + '-' + getVersionAT() + '-' + classifier + '.' + ext);
    }

    public String getDependencyString() {
        String ret = GROUP + ':' + NAME + ':' + VERSION;
        if (MAPPING != null)
            ret += "_mapped_" + MAPPING;
        if (AT_HASH != null)
            ret += "_at_" + AT_HASH;
        //ret = "rnd." + (new Random().nextInt()) + "." + ret; //Stupid hack to make gradle always try and ask for this file. This should be removed once we figure out why the hell gradle just randomly decides to not try to resolve us!
        return ret;
    }

    private String getATHash(String version) {
        if (!version.contains("_at_"))
            return null;
        return version.split("_at_")[1];
    }
    private String getMappings(String version) {
        if (!version.contains("_mapped_"))
            return null;
        return version.split("_mapped_")[1];
    }

    private String getVersion(String mappings) {
        return mappings == null ? VERSION : VERSION + "_mapped_" + mappings;
    }
    private String getVersionWithAT(String mappings) {
        if (AT_HASH == null) return getVersion(mappings);
        return getVersion(mappings) + "_at_" + AT_HASH;
    }
    private String getVersionAT() {
        if (AT_HASH == null) return VERSION;
        return VERSION + "_at_" + AT_HASH;
    }

    private Patcher getParents() {
        if (!loadedParents) {

            String classifier = "userdev";
            if ("net.minecraftforge".equals(GROUP) && "forge".equals(NAME)) {
                MinecraftVersion mcver = MinecraftVersion.from(VERSION.split("-")[0]);
                if (mcver.compareTo(v1_13) < 0)
                    classifier = "userdev3";
            }

            String artifact = isPatcher ? (GROUP + ":" + NAME +":" + VERSION + ':' + classifier) :
                                        ("de.oceanlabs.mcp:mcp_config:" + VERSION + "@zip");
            boolean patcher = isPatcher;
            Patcher last = null;
            while (artifact != null) {
                debug("    Parent: " + artifact);
                File dep = MavenArtifactDownloader.manual(project, artifact, CHANGING_USERDEV);
                if (dep == null)
                    throw new IllegalStateException("Could not resolve dependency: " + artifact);
                if (patcher) {
                    Patcher _new = new Patcher(project, dep, artifact);
                    if (parent == null)
                        parent = _new;
                    if (last != null)
                        last.setParent(_new);
                    last = _new;

                    patcher = !_new.parentIsMcp();
                    artifact = _new.getParentDesc();
                } else {
                    mcp = new MCP(dep, artifact);
                    break;
                }
            }
            loadedParents = mcp != null;
        }
        return parent;
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String group = artifact.getGroup();
        String rand = "";
        if (group.startsWith("rnd.")) {
            rand = group.substring(0, group.indexOf('.', 4));
            group = group.substring(group.indexOf('.', 4) + 1);
        }
        String version = artifact.getVersion();
        String athash = getATHash(version); //There is no way to reverse the ATs from the hash, so this is just to make Gradle request a new file if they change.
        if (athash != null)
            version = version.substring(0, version.length() - (athash.length() + "_at_".length()));

        String mappings = getMappings(version);
        if (mappings != null)
            version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));

        if (!group.equals(GROUP) || !artifact.getName().equals(NAME) || !version.equals(VERSION))
            return null;

        if ((AT_HASH == null && athash != null) || (AT_HASH != null && !AT_HASH.equals(athash)))
            return null;

        if (!isPatcher && mappings == null) //net.minecraft in obf names. We don't do that.
            return null;

        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension();

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            return findPom(mappings, rand);
        } else {
            switch (classifier) {
                case "":        return findRaw(mappings);
                case "sources": return findSource(mappings, true);
                default:        return findExtraClassifier(mappings, classifier, ext);
            }
        }
    }

    private HashStore commonHash(File mapping) {
        getParents();
        HashStore ret = new HashStore(this.getCacheRoot());
        ret.add(mcp.artifact.getDescriptor(), mcp.getZip());
        Patcher patcher = parent;
        while (patcher != null) {
            ret.add(parent.artifact.getDescriptor(), parent.data);
            patcher = patcher.getParent();
        }
        if (mapping != null)
            ret.add("mapping", mapping);
        if (AT_HASH != null)
            ret.add("ats", AT_HASH);

        return ret;
    }

    private File findMapping(String mapping) {
        if (mapping == null) {
            debug("  FindMappings: Null mappings");
            return null;
        }

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = MCPRepo.getMappingDep(channel, version);
        debug("    Mapping: " + desc);

        File ret = MavenArtifactDownloader.generate(project, desc, CHANGING_USERDEV);
        if (ret == null) {
            String message = "Could not download MCP Mappings: " + desc;
            debug ("    " + message);
            project.getLogger().error(message);
            throw new IllegalStateException(message);
        }
        return ret;
    }

    private File findPom(String mapping, String rand) throws IOException {
        getParents(); //Download parents
        if (mcp == null || mapping == null) {
            debug("  Finding Pom: MCP or Mappings were null");
            return null;
        }

        File pom = cacheMapped(mapping, "pom");
        if (!rand.isEmpty()) {
            rand += '.';
            pom = cacheMapped(mapping, rand + "pom");
        }

        HashStore cache = commonHash(null).load(new File(pom.getAbsolutePath() + ".input"));

        if (cache.isSame() && pom.exists()) {
            debug("  Finding Pom: Cache Hit");
        } else {
            debug("  Finding Pom: " + pom);
            POMBuilder builder = new POMBuilder(rand + GROUP, NAME, getVersionWithAT(mapping) );

            //builder.dependencies().add(rand + GROUP + ':' + NAME + ':' + getVersionWithAT(mapping), "compile"); //Normal poms dont reference themselves...
            builder.dependencies().add("net.minecraft:client:" + mcp.getMCVersion() + ":extra", "compile"); //Client as that has all deps as external list
            mcp.getLibraries().forEach(e -> builder.dependencies().add(e, "compile"));

            if (mapping != null) {
                int idx = mapping.lastIndexOf('_');
                String channel = mapping.substring(0, idx);
                String version = mapping.substring(idx + 1);
                builder.dependencies().add(MCPRepo.getMappingDep(channel, version), "compile"); //Runtime?
            }

            Patcher patcher = parent;
            while (patcher != null) {
                for (String lib : patcher.getLibraries()) {
                    Artifact af = Artifact.from(lib);
                    //Gradle only allows one dependency with the same group:name. So if we depend on any claissified deps, repackage it ourselves.
                    // Gradle also seems to not be able to reference itself. So we add it elseware.
                    if (GROUP.equals(af.getGroup()) && NAME.equals(af.getName()) && VERSION.equals(af.getVersion())) {
                        builder.dependencies().add(rand + GROUP, NAME, getVersionWithAT(mapping), af.getClassifier(), af.getExtension(), "compile");
                    } else {
                        builder.dependencies().add(lib, "compile");
                    }
                }
                patcher = patcher.getParent();
            }

            String ret = builder.tryBuild();
            if (ret == null) {
                return null;
            }
            FileUtils.writeByteArrayToFile(pom, ret.getBytes(StandardCharsets.UTF_8));
            cache.save();
            Utils.updateHash(pom, HashFunction.SHA1);
        }

        return pom;
    }

    private File findBinPatches() throws IOException {
        File ret = cacheRaw("binpatches", "lzma");
        HashStore cache = new HashStore().load(cacheRaw("binpatches", "lzma.input"))
                .add("parent", parent.getZip());

        if (cache.isSame() && ret.exists()) {
            debug("  FindBinPatches: Cache Hit");
        } else {
            debug("  FindBinPatches: Extracting to " + ret);
            try (ZipFile zip = new ZipFile(parent.getZip())) {
                Utils.extractFile(zip, parent.getConfig().binpatches, ret);
                cache.save();
            }
        }

        return ret;
    }

    private File findRaw(String mapping) throws IOException {
        File names = findMapping(mapping);
        HashStore cache = commonHash(names)
            .add("codever", "2");

        if (mapping != null && names == null) {
            debug("  Finding Raw: Could not find names, exiting");
            return null;
        }

        File recomp = findRecomp(mapping, false);
        if (recomp != null) {
            debug("  Finding Raw: Returning Recomp: " + recomp);
            return recomp;
        }

        if (mapping == null && parent == null) {
            debug("  Finding Raw: Userdev does not provide SRG Minecraft");
            return null;
        }

        File bin = cacheMapped(mapping, "jar");
        cache.load(cacheMapped(mapping, "jar.input"));
        if (cache.isSame() && bin.exists()) {
            debug("  Finding Raw: Cache Hit: " + bin);
        } else {
            debug("  Finding Raw: Cache Miss");
            StringBuilder baseAT = new StringBuilder();

            for (Patcher patcher = parent; patcher != null; patcher = patcher.parent) {
                if (patcher.getATData() != null && !patcher.getATData().isEmpty()) {
                    if (baseAT.length() != 0)
                        baseAT.append("\n===========================================================\n");
                    baseAT.append(patcher.getATData());
                }
            }
            boolean hasAts = baseAT.length() != 0 || !ATS.isEmpty();
            debug("    HasAts: " + hasAts);

            Set<String> packages = new HashSet<>();
            File srged = findBinpatched(packages);

            File mcinject = cacheRaw("mci", "jar");

            debug("    Applying MCInjector");
            //Apply MCInjector so we can compile against this jar
            ApplyMCPFunction mci = createTask(MCI_JAR_TASK_PREFIX, ApplyMCPFunction.class);
            mci.setFunctionName("mcinject");
            mci.setHasLog(false);
            mci.setInput(srged);
            mci.setMCP(mcp.getZip());
            mci.setOutput(mcinject);
            mci.apply();

            debug("    Creating MCP Inject Sources");
            //Build and inject MCP injected sources
            File inject_src = cacheRaw("inject_src", "jar");
            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(mcp.getZip()));
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(inject_src)) ) {
                String prefix = mcp.wrapper.getConfig().getData("inject");
                String template = null;
                ZipEntry entry = null;
                while ((entry = zin.getNextEntry()) != null) {
                    if (!entry.getName().startsWith(prefix) || entry.isDirectory())
                        continue;

                    // If an entry has a specific side in its name, don't apply
                    // it when we're on the opposite side. Entries without a specific
                    // side should always be applied
                    if ("server".equals(NAME) && entry.getName().contains("/client/")) {
                        continue;
                    }

                    if ("client".equals(NAME) && entry.getName().contains("/server/")) {
                        continue;
                    }

                    String name = entry.getName().substring(prefix.length());
                    if ("package-info-template.java".equals(name)) {
                        template = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    } else {
                        zos.putNextEntry(Utils.getStableEntry(name));
                        IOUtils.copy(zin, zos);
                        zos.closeEntry();
                    }
                }

                if (template != null) {
                    for (String pkg : packages) {
                        zos.putNextEntry(Utils.getStableEntry(pkg + "/package-info.java"));
                        zos.write(template.replace("{PACKAGE}", pkg.replace("/", ".")).getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }

            debug("    Compiling MCP Inject sources");
            File compiled = compileJava(inject_src, mcinject);
            if (compiled == null)
                return null;

            debug("    Injecting MCP Inject binairies");
            File injected = cacheRaw("injected", "jar");
            //Combine mci, and our recompiled MCP injected classes.
            try (ZipInputStream zmci = new ZipInputStream(new FileInputStream(mcinject));
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(injected))) {
                ZipEntry entry = null;
                while ((entry = zmci.getNextEntry()) != null) {
                    zout.putNextEntry(Utils.getStableEntry(entry.getName()));
                    IOUtils.copy(zmci, zout);
                    zout.closeEntry();
                }
                Files.walkFileTree(compiled.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try (InputStream fin = Files.newInputStream(file)) {
                            zout.putNextEntry(Utils.getStableEntry(compiled.toPath().relativize(file).toString().replace('\\', '/')));
                            IOUtils.copy(fin, zout);
                            zout.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            if (hasAts) {
                if (bin.exists()) bin.delete(); // AT lib throws an exception if output file already exists

                debug("    Applying Access Transformer");
                AccessTransformJar at = createTask(ACCESS_TRANSFORM_JAR_TASK_PREFIX, AccessTransformJar.class);
                at.setInput(injected);
                at.setOutput(bin);
                at.setAts(ATS);

                if (baseAT.length() != 0) {
                    File parentAT = project.file("build/" + at.getName() + "/parent_at.cfg");
                    if (!parentAT.getParentFile().exists())
                        parentAT.getParentFile().mkdirs();
                    Files.write(parentAT.toPath(), baseAT.toString().getBytes(StandardCharsets.UTF_8));
                    at.setAts(parentAT);
                }

                at.apply();
            }

            if (mapping == null) { //They didn't ask for MCP names, so serve them SRG!
                FileUtils.copyFile(injected, bin);
            } else if (hasAts) {
                debug("    Renaming ATed Jar in place");
                //Remap library to MCP names, in place, sorta hacky with ATs but it should work.
                RenameJarInPlace rename = createTask(RENAME_JAR_IN_PLACE_TASK_PREFIX, RenameJarInPlace.class);
                rename.setHasLog(false);
                rename.setInput(bin);
                rename.setMappings(findSrgToMcp(mapping, names));
                rename.apply();
            } else {
                debug("    Renaming injected jar");
                //Remap library to MCP names
                RenameJar rename = createTask(RENAME_JAR_TASK_PREFIX, RenameJar.class);
                rename.setHasLog(false);
                rename.setInput(injected);
                rename.setOutput(bin);
                rename.setMappings(findSrgToMcp(mapping, names));
                rename.apply();
            }

            debug("    Finished: " + bin);
            Utils.updateHash(bin, HashFunction.SHA1);
            cache.save();
        }
        return bin;
    }

    private File findBinpatched(final Set<String> packages) throws IOException {
        boolean notch = parent != null && parent.getConfigV2() != null && parent.getConfigV2().getNotchObf();

        String desc = "net.minecraft:" + (isPatcher ? "joined" : NAME) + ":" + (notch ? mcp.getMCVersion() : mcp.getVersion() + ":srg");
        File clean = MavenArtifactDownloader.generate(project, desc, true);

        if (clean == null || !clean.exists()) {
            debug("  Failed to find MC Vanilla Base: " + desc);
            project.getLogger().error("MinecraftUserRepo: Failed to get Minecraft Vanilla Base. Should not be possible. " + desc);
            return null;
        }
        debug("    Vanilla Base: " + clean);

        File obf2Srg = null;
        try (ZipFile tmp = new ZipFile(clean)) {
            if (notch) {
                obf2Srg = findObfToSrg(IMappingFile.Format.TSRG);
                if (obf2Srg == null) {
                    debug("  Failed to find obf to mcp mapping file. " + mcp.getVersion());
                    project.getLogger().error("MinecraftUserRepo: Failed to find obf to mcp mapping file. Should not be possible. " + mcp.getVersion());
                    return null;
                }

                Set<String> vanillaClasses = tmp.stream()
                .map(ZipEntry::getName)
                .filter(e -> e.endsWith(".class"))
                .map(e -> e.substring(0, e.length() - 6))
                .collect(Collectors.toSet());

                IMappingFile o2s = IMappingFile.load(obf2Srg);
                o2s.getClasses().stream()
                .filter(e -> vanillaClasses.contains(e.getOriginal()))
                .map(IMappingFile.INode::getMapped)
                .map(e -> e.indexOf('/') == -1 ? "" : e.substring(0, e.lastIndexOf('/')))
                .forEach(packages::add);

            } else {
                //Gather vanilla packages, so we can only inject the proper package-info classes.
                tmp.stream()
                .map(ZipEntry::getName)
                .filter(e -> e.endsWith(".class"))
                .map(e -> e.indexOf('/') == -1 ? "" : e.substring(0, e.lastIndexOf('/')))
                .forEach(packages::add);
            }
        }

        if (parent == null) { //Raw minecraft
            return clean;
        } else { // Needs binpatches
            File binpatched = cacheRaw("binpatched", "jar");

            debug("    Creating Binpatches");
            //Apply bin patches to vanilla
            ApplyBinPatches apply = createTask(APPLY_BINPATCHES_TASK_PREFIX, ApplyBinPatches.class);
            apply.setHasLog(true);
            apply.getTool().set(parent.getConfig().binpatcher.getVersion());
            apply.getArgs().set(parent.getConfig().binpatcher.getArgs());
            apply.getClean().set(clean);
            apply.getPatch().set(findBinPatches());
            apply.getOutput().set(binpatched);
            apply.apply();

            debug("    Injecting binpatch extras");
            File merged = cacheRaw(notch ? "obf" : "srg", "jar");

            //Combine all universals and vanilla together.
            Set<String> added = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(merged))) {

                //Add binpatched, then vanilla. First seen overrides any other entries
                for (File file : new File[] {binpatched, clean}) {
                    try (ZipInputStream zin = new ZipInputStream(new FileInputStream(file))) {
                        ZipEntry entry;
                        while ((entry = zin.getNextEntry()) != null) {
                            String name = entry.getName();
                            if (added.contains(name))
                                continue;
                            ZipEntry _new = new ZipEntry(name);
                            _new.setTime(entry.getTime()); //Should be stable, but keeping time.
                            zip.putNextEntry(_new);
                            IOUtils.copy(zin, zip);
                            added.add(name);
                        }
                    }
                }

                copyResources(zip, added, true);
            }

            if (notch) {
                File srged = cacheRaw("srg", "jar");
                debug("    Renaming injected jar");
                //Remap to SRG names
                RenameJar rename = createTask(RENAME_JAR_TASK_PREFIX, RenameJar.class);
                rename.setHasLog(false);
                rename.setInput(merged);
                rename.setOutput(srged);
                rename.setMappings(obf2Srg);
                rename.apply();
                return srged;
            } else {
                return merged;
            }
        }
    }

    private void copyResources(ZipOutputStream zip, Set<String> added, boolean includeClasses) throws IOException {
        Map<String, List<String>> servicesLists = new HashMap<>();
        Predicate<String> filter = (name) ->
            added.contains(name) ||
            (!includeClasses && name.endsWith(".class")) ||
            (name.startsWith("META-INF") && (name.endsWith(".DSA") || name.endsWith(".SF")));

        // Walk parents and combine from bottom up so we get any overridden files.
        Patcher patcher = parent;
        while (patcher != null) {
            if (patcher.getUniversal() != null) {
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getUniversal()))) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (filter.test(name))
                            continue;
                        if (parent.getUniversalFilters().stream().anyMatch(f -> !f.matcher(name).matches()))
                            continue;

                        if (name.startsWith("META-INF/services/") && !entry.isDirectory()) {
                            List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                            if (existing.size() > 0) existing.add("");
                            existing.add(String.format("# %s - %s", patcher.artifact, patcher.getUniversal().getCanonicalFile().getName()));
                            existing.addAll(IOUtils.readLines(zin, StandardCharsets.UTF_8));
                        } else {
                            ZipEntry _new = new ZipEntry(name);
                            _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
                            zip.putNextEntry(_new);
                            IOUtils.copy(zin, zip);
                            added.add(name);
                        }
                    }
                }
            }
            // Dev time specific files, such as launch helper.
            if (patcher.getInject() != null) {
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getZip()))) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        if (!entry.getName().startsWith(patcher.getInject()) || entry.getName().length() <= patcher.getInject().length())
                            continue;

                        String name = entry.getName().substring(patcher.getInject().length());
                        if (filter.test(name))
                            continue;

                        if (name.startsWith("META-INF/services/") && !entry.isDirectory()) {
                            List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                            if (existing.size() > 0) existing.add("");
                            existing.add(String.format("# %s - %s", patcher.artifact, patcher.getZip().getCanonicalFile().getName()));
                            existing.addAll(IOUtils.readLines(zin, StandardCharsets.UTF_8));
                        } else {
                            ZipEntry _new = new ZipEntry(name);
                            _new.setTime(0);
                            zip.putNextEntry(_new);
                            IOUtils.copy(zin, zip);
                            added.add(name);
                        }
                    }
                }
            }
            patcher = patcher.getParent();
        }

        for(Map.Entry<String, List<String>> kv : servicesLists.entrySet()) {
            String name = kv.getKey();
            ZipEntry _new = new ZipEntry(name);
            _new.setTime(0);
            zip.putNextEntry(_new);
            // JAR File Specification requires UTF-8 encoding here
            IOUtils.writeLines(kv.getValue(), "\n", zip, StandardCharsets.UTF_8);
            added.add(name);
        }
    }

    private McpNames loadMCPNames(String name, File data) throws IOException {
        McpNames map = mapCache.get(name);
        String hash = HashFunction.SHA1.hash(data);
        if (map == null || !hash.equals(map.hash)) {
            map = McpNames.load(data);
            mapCache.put(name, map);
        }
        return map;
    }

    private File findObfToSrg(IMappingFile.Format format) throws IOException {
        String ext = format.name().toLowerCase();
        File root = cache(mcp.getArtifact().getGroup().replace('.', '/'), mcp.getArtifact().getName(), mcp.getArtifact().getVersion());
        File file = new File(root, "obf_to_srg." + ext);

        HashStore cache = new HashStore()
            .add("mcp", mcp.getZip())
            .load(new File(root, "obf_to_srg." + ext + ".input"));

        if (!cache.isSame() || !file.exists()) {
            byte[] data = mcp.getData("mappings");
            IMappingFile obf_to_srg = IMappingFile.load(new ByteArrayInputStream(data));
            obf_to_srg.write(file.toPath(), format, false);
            cache.save();
        }

        return file;
    }

    private File findSrgToMcp(String mapping, File names) throws IOException {
        if (names == null) {
            debug("Attempted to create SRG to MCP with null MCP mappings: " + mapping);
            throw new IllegalArgumentException("Attempted to create SRG to MCP with null MCP mappings: " + mapping);
        }
        File root = cache(mcp.getArtifact().getGroup().replace('.', '/'), mcp.getArtifact().getName(), mcp.getArtifact().getVersion());
        String srg_name = "srg_to_" + mapping + ".tsrg";
        File srg = new File(root, srg_name);

        HashStore cache = new HashStore()
            .add("mcp", mcp.getZip())
            .add("mapping", names)
            .load(new File(root, srg_name + ".input"));

        if (!cache.isSame() || !srg.exists()) {
            info("Creating SRG -> MCP TSRG");
            byte[] data = mcp.getData("mappings");
            McpNames mcp_names = loadMCPNames(mapping, names);
            IMappingFile obf_to_srg = IMappingFile.load(new ByteArrayInputStream(data));
            IMappingFile srg_to_named = obf_to_srg.reverse().chain(obf_to_srg).rename(new IRenamer() {
                @Override
                public String rename(IField value) {
                    return mcp_names.rename(value.getMapped());
                }

                @Override
                public String rename(IMethod value) {
                    return mcp_names.rename(value.getMapped());
                }
            });

            srg_to_named.write(srg.toPath(), IMappingFile.Format.TSRG, false);
            cache.save();
        }

        return srg;
    }

    private File findDecomp(boolean generate) throws IOException {
        HashStore cache = commonHash(null);

        File decomp = cacheAT("decomp", "jar");
        debug("  Finding Decomp: " + decomp);
        cache.load(cacheAT("decomp", "jar.input"));

        if (cache.isSame() && decomp.exists()) {
            debug("  Cache Hit");
        } else if (decomp.exists() || generate) {
            debug("  Decompiling");
            File output = mcp.getStepOutput(isPatcher ? "joined" : NAME, null);
            if (parent != null && parent.getConfigV2() != null && parent.getConfigV2().processor != null) {
                DataFunction data = parent.getConfigV2().processor;
                DynamicJarExec proc = createTask(POST_PROCESS_TASK_PREFIX, DynamicJarExec.class);
                proc.getInput().set(output);
                proc.getOutput().set(decomp);
                proc.getTool().set(data.getVersion());
                proc.getArgs().set(data.getArgs());

                if (data.getData() != null) {
                    File root = project.file("build/" + proc.getName());
                    if (!root.exists())
                        root.mkdirs();

                    try (final ZipFile zip = new ZipFile(parent.getZip())) {
                        for (Entry<String, String> ent : data.getData().entrySet()) {
                            File target = new File(root, ent.getValue());
                            Utils.extractFile(zip, ent.getValue(), target);
                            proc.getData().put(ent.getKey(), target);
                        }
                    }
                }

                proc.apply();
            } else {
                FileUtils.copyFile(output, decomp);
            }
            cache.save();
            Utils.updateHash(decomp, HashFunction.SHA1);
        }
        return decomp.exists() ? decomp : null;
    }

    private File findPatched(boolean generate) throws IOException {
        File decomp = findDecomp(generate);
        if (decomp == null || !decomp.exists()) {
            debug("  Finding Patched: Decomp not found");
            return null;
        }
        if (parent == null) {
            debug("  Finding Patched: No parent");
            return decomp;
        }

        HashStore cache = commonHash(null).add("decomp", decomp);

        File patched = cacheAT("patched", "jar");
        debug("  Finding patched: " + decomp);
        cache.load(cacheAT("patched", "jar.input"));

        if (cache.isSame() && patched.exists()) {
            debug("    Cache Hit");
        } else if (patched.exists() || generate) {
            debug("    Generating");
            LinkedList<Patcher> parents = new LinkedList<>();
            Patcher patcher = parent;
            while (patcher != null) {
                parents.addFirst(patcher);
                patcher = patcher.getParent();
            }

            boolean failed = false;
            byte[] lastPatched = FileUtils.readFileToByteArray(decomp);
            for (Patcher p : parents) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                PatchOperation.Builder opBuilder = PatchOperation.builder()
                        .logTo(new LoggingOutputStream(project.getLogger(), LogLevel.LIFECYCLE))
                        .basePath(lastPatched, ArchiveFormat.ZIP)
                        .patchesPath(p.getZip().toPath())
                        .patchesPrefix(p.getPatches())
                        .outputPath(bout, ArchiveFormat.ZIP)
                        .mode(PatchMode.ACCESS)
                        .verbose(DEBUG)
                        .summary(DEBUG);
                // Note that pre-1.13 patches use ../{src-base,src-work}/minecraft/ prefixes
                // instead of the default {a,b}/ prefixes. Also, be sure not to override the
                // defaults with null values.
                UserdevConfigV2 cfg = p.getConfigV2();
                if (cfg != null) {
                    if (cfg.patchesOriginalPrefix != null) {
                        opBuilder = opBuilder.aPrefix(cfg.patchesOriginalPrefix);
                    }
                    if (cfg.patchesModifiedPrefix != null) {
                        opBuilder = opBuilder.bPrefix(cfg.patchesModifiedPrefix);
                    }
                }
                CliOperation.Result<PatchOperation.PatchesSummary> result = opBuilder
                        .build()
                        .operate();
                failed = result.exit != 0;
                if (failed) {
                    break; //Pointless errors if we continue.
                }
                lastPatched = bout.toByteArray();
            }
            if (failed)
                throw new RuntimeException("Failed to apply patches to source file, see log for details: " + decomp);

            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(patched))) {
                Set<String> added = new HashSet<>();
                if (lastPatched != null) {
                    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(lastPatched))) {
                        added.addAll(Utils.copyZipEntries(zout, zin, e -> true));
                    }
                }
                debug("    Injecting patcher extras");
                // Walk parents and combine from bottom up so we get any overridden files.
                patcher = parent;
                while (patcher != null) {
                    if (patcher.getSources() != null) {
                        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getSources()))) {
                            added.addAll(Utils.copyZipEntries(zout, zin, e -> !added.contains(e) && !e.startsWith("patches/"))); //Skip patches, as they are included in src for reference.
                        }
                    }
                    patcher = patcher.getParent();
                }

                cache.save();
                Utils.updateHash(patched, HashFunction.SHA1);
            }
        }
        return patched.exists() ? patched : null;
    }

    private File findSource(String mapping, boolean generate) throws IOException {
        File patched = findPatched(generate);
        if (patched == null || !patched.exists()) {
            debug("  Finding Source: Patched not found");
            return null;
        }

        if (mapping == null) {
            debug("  Finding Source: No Renames");
            return patched;
        }

        File names = findMapping(mapping);
        if (mapping != null && names == null) {
            debug("  Finding Sources: Mapping not found");
            return null;
        }

        File obf2srg = findObfToSrg(IMappingFile.Format.TSRG);
        if (obf2srg == null) {
            debug("  Finding Source: No obf2srg");
            return patched;
        }

        HashStore cache = commonHash(names);

        File sources = cacheMapped(mapping, "sources", "jar");
        debug("  Finding Source: " + sources);
        cache.load(cacheMapped(mapping, "sources", "jar.input"));
        if (cache.isSame() && sources.exists()) {
            debug("    Cache hit");
        } else if (sources.exists() || generate) {
            IMappingFile obf_to_srg = IMappingFile.load(obf2srg);
            Set<String> vanilla = obf_to_srg.getClasses().stream().map(IMappingFile.INode::getMapped).collect(Collectors.toSet());

            McpNames map = McpNames.load(names);

            if (!sources.getParentFile().exists())
                sources.getParentFile().mkdirs();

            boolean addJavadocs = parent == null || parent.getConfigV2() == null || parent.getConfigV2().processor == null;
            Charset sourceFileCharset = parent == null || parent.getConfigV2() == null ? StandardCharsets.UTF_8 :
                    Charset.forName(parent.getConfigV2().getSourceFileCharset());
            debug("    Renaming Sources, Javadocs: " + addJavadocs);
            try(ZipInputStream zin = new ZipInputStream(new FileInputStream(patched));
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(sources))) {
                ZipEntry _old;
                while ((_old = zin.getNextEntry()) != null) {
                    String name = _old.getName();
                    zout.putNextEntry(Utils.getStableEntry(name));

                    if (name.endsWith(".java")) {
                        String mapped = map.rename(zin,
                                addJavadocs && vanilla.contains(name.substring(0, name.length() - 5)),
                                true, sourceFileCharset);
                        IOUtils.write(mapped, zout, sourceFileCharset);
                    } else {
                        IOUtils.copy(zin, zout);
                    }
                }
            }

            Utils.updateHash(sources, HashFunction.SHA1);
            cache.save();
        }
        return sources.exists() ? sources : null;
    }

    private File findRecomp(String mapping, boolean generate) throws IOException {
        File source = findSource(mapping, generate);
        if (source == null || !source.exists()) {
            debug("  Finding Recomp: Sources not found");
            return null;
        }
        File names = findMapping(mapping);
        if (names == null && mapping != null) {
            debug("  Finding Recomp: Could not find names");
            return null;
        }

        HashStore cache = commonHash(names);
        cache.add("source", source);
        cache.load(cacheMapped(mapping, "recomp", "jar.input"));

        File recomp = cacheMapped(mapping, "recomp", "jar");

        if (cache.isSame() && recomp.exists()) {
            debug("  Finding Recomp: Cache Hit");
        } else {
            debug("  Finding recomp: " + cache.isSame() + " " + recomp);

            debug("    Compiling");
            File compiled = compileJava(source);
            if (compiled == null) {
                debug("    Compiling failed");
                throw new IllegalStateException("Compile failed in findRecomp. See log for more details");
            }

            debug("    Injecting resources");
            Set<String> added = new HashSet<>();
            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(recomp))) {
                //Add all compiled code
                Files.walkFileTree(compiled.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try (InputStream fin = Files.newInputStream(file)) {
                            String name = compiled.toPath().relativize(file).toString().replace('\\', '/');
                            zout.putNextEntry(Utils.getStableEntry(name));
                            IOUtils.copy(fin, zout);
                            zout.closeEntry();
                            added.add(name);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                copyResources(zout, added, false);
            }
            Utils.updateHash(recomp, HashFunction.SHA1);
            cache.save();
        }
        return recomp;
    }

    private File findExtraClassifier(String mapping, String classifier, String extension) throws IOException {
        //These are extra classifiers shipped by the normal repo. Except that gradle doesn't allow two artifacts with the same group:name
        // but different version. For good reason. So we change their version to ours. And provide them as is.

        File target = cacheMapped(mapping, classifier, extension);
        debug("  Finding Classified: " + target);

        File original = MavenArtifactDownloader.manual(project, Artifact.from(GROUP, NAME, VERSION, classifier, extension).getDescriptor(), CHANGING_USERDEV);
        HashStore cache = commonHash(null); //TODO: Remap from SRG?
        if (original != null)
            cache.add("original", original);

        cache.load(cacheMapped(mapping, classifier, extension + ".input"));
        if (cache.isSame() && target.exists()) {
            debug ("    Cache hit");
        } else {
            if (original == null) {
                debug("    Failed to download original artifact.");
                return null;
            }
            debug("    Copying file");
            try {
                FileUtils.copyFile(original, target);
            } catch (IOException e) { //Something screwed up, nuke the file incase its invalid and return nothing.
                if (target.exists())
                    target.delete();
            }
            cache.save();
            Utils.updateHash(target, HashFunction.SHA1);
        }
        return target;
    }

    private String getNextTaskName(String prefix) {
        return '_' + prefix + "_" + compileTaskCount++;
    }
    private <T extends Task> T createTask(String prefix, Class<T> cls) {
        return project.getTasks().create(getNextTaskName(prefix), cls);
    }

    private int compileTaskCount = 1;
    private File compileJava(File source, File... extraDeps) {
        HackyJavaCompile compile = createTask(COMPILE_JAVA_TASK_PREFIX, HackyJavaCompile.class);
        try {
            File output = project.file("build/" + compile.getName() + "/");
            if (output.exists()) {
                FileUtils.cleanDirectory(output);
            } else {
                // Due to the weird way that we invoke JavaCompile,
                // we need to ensure that the output directory already exists
                output.mkdirs();
            }
            Set<File> files = Sets.newHashSet(this.extraDataFiles);
            Collections.addAll(files, extraDeps);
            compile.setClasspath(project.files(files));
            if (parent != null) {
                compile.setSourceCompatibility(parent.getConfig().getSourceCompatibility());
                compile.setTargetCompatibility(parent.getConfig().getTargetCompatibility());
            } else {
                final JavaPluginConvention java = project.getConvention().findPlugin(JavaPluginConvention.class);
                if (java != null) {
                    compile.setSourceCompatibility(java.getSourceCompatibility().toString());
                    compile.setTargetCompatibility(java.getTargetCompatibility().toString());
                }
            }
            compile.setDestinationDir(output);
            compile.setSource(source.isDirectory() ? project.fileTree(source) : project.zipTree(source));

            compile.doHackyCompile();

            return output;
        } catch (Exception e) { //Compile errors...?
            e.printStackTrace();
            return null;
        } finally {
            compile.setEnabled(false);
        }
    }

    private static class Patcher {
        private final File data;
        private final File universal;
        private final File sources;
        private final Artifact artifact;
        private final UserdevConfigV1 config;
        private final UserdevConfigV2 configv2;
        private Patcher parent;
        private String ATs = null;
        private String SASs = null;
        private List<Pattern> universalFilters;

        private Patcher(Project project, File data, String artifact) {
            this.data = data;
            this.artifact = Artifact.from(artifact);

            try {
                byte[] cfg_data = Utils.getZipData(data, "config.json");
                int spec = Config.getSpec(cfg_data);

                if (spec == 1) {
                    this.config = UserdevConfigV1.get(cfg_data);
                    this.configv2 = null;
                } else if (spec == 2) {
                    this.configv2 = UserdevConfigV2.get(cfg_data);
                    this.config = this.configv2;
                } else {
                    throw new IllegalStateException("Could not load Patcher config, Unknown Spec: " + spec + " Dep: " + artifact);
                }

                if (getParentDesc() == null)
                    throw new IllegalStateException("Invalid patcher dependency, missing MCP or parent: " + artifact);

                if (config.universal != null) {
                    universal = MavenArtifactDownloader.manual(project, config.universal, CHANGING_USERDEV);
                    if (universal == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve universal: " + universal);
                } else {
                    universal = null;
                }

                if (config.sources != null) {
                    sources = MavenArtifactDownloader.manual(project, config.sources, CHANGING_USERDEV);
                    if (sources == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve sources: " + sources);
                } else {
                    sources = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public UserdevConfigV1 getConfig() {
            return config;
        }
        public UserdevConfigV2 getConfigV2() {
            return this.configv2;
        }
        public boolean parentIsMcp() {
            return this.config.mcp != null;
        }

        public void setParent(Patcher value) {
            this.parent = value;
        }
        public Patcher getParent() {
            return this.parent;
        }

        public String getParentDesc() {
            return this.config.mcp != null ? this.config.mcp : this.config.parent;
        }

        public List<String> getLibraries() {
            return this.config.libraries == null ? Collections.emptyList() : this.config.libraries;
        }

        public String getATData() {
            if (config.getATs().isEmpty())
                return null;

            if (ATs == null) {
                StringBuilder buf = new StringBuilder();
                try (ZipFile zip = new ZipFile(data)) {
                    for (String at : config.getATs()) {
                        ZipEntry entry = zip.getEntry(at);
                        if (entry == null)
                            throw new IllegalStateException("Invalid Patcher config, Missing Access Transformer: " + at + " Zip: " + data);
                        buf.append("# ").append(artifact).append(" - ").append(at).append('\n');
                        buf.append(IOUtils.toString(zip.getInputStream(entry), StandardCharsets.UTF_8));
                        buf.append('\n');
                    }
                    ATs = buf.toString();
                } catch (IOException e) {
                    throw new RuntimeException("Invalid patcher config: " + artifact, e);
                }
            }
            return ATs;
        }

        public String getSASData() {
            if (config.getSASs().isEmpty())
                return null;

            if (SASs == null) {
                StringBuilder buf = new StringBuilder();
                try (ZipFile zip = new ZipFile(data)) {
                    for (String sas : config.getSASs()) {
                        ZipEntry entry = zip.getEntry(sas);
                        if (entry == null)
                            throw new IllegalStateException("Invalid Patcher config, Missing Side Annotation Stripper: " + sas + " Zip: " + data);
                        buf.append("# ").append(artifact).append(" - ").append(sas).append('\n');
                        buf.append(IOUtils.toString(zip.getInputStream(entry), StandardCharsets.UTF_8));
                        buf.append('\n');
                    }
                    SASs = buf.toString();
                } catch (IOException e) {
                    throw new RuntimeException("Invalid patcher config: " + artifact, e);
                }
            }
            return SASs;
        }

        public File getZip() {
            return data;
        }

        public File getUniversal() {
            return universal;
        }
        public File getSources() {
            return sources;
        }

        public String getInject() {
            return config.inject;
        }

        public String getPatches() {
            return config.patches;
        }

        public List<Pattern> getUniversalFilters() {
            if (this.universalFilters == null) {
                this.universalFilters = new ArrayList<>();
                if (getConfigV2() != null) {
                    for (String filter : getConfigV2().getUniversalFilters())
                        this.universalFilters.add(Pattern.compile(filter));
                }
            }
            return this.universalFilters;
        }
    }

    private class MCP {
        private final MCPWrapper wrapper;
        private final Artifact artifact;

        private MCP(File data, String artifact) {
            this.artifact = Artifact.from(artifact);
            try {
                File mcp_dir = MinecraftUserRepo.this.cache("mcp", this.artifact.getVersion());
                this.wrapper = new MCPWrapper(data, mcp_dir) {
                    public MCPRuntime getRuntime(Project project, String side) {
                        MCPRuntime ret = runtimes.get(side);
                        if (ret == null) {
                            File dir = new File(wrapper.getRoot(), side);
                            List<String> ats = new ArrayList<>();
                            List<String> sas = new ArrayList<>();

                            if (AT_HASH != null)
                                dir = new File(dir, AT_HASH);

                            Patcher patcher = MinecraftUserRepo.this.parent;
                            while (patcher != null) {
                                String data = patcher.getATData();
                                if (data != null && !data.isEmpty())
                                    ats.add(data);

                                data = patcher.getSASData();
                                if (data != null && !data.isEmpty())
                                    sas.add(data);

                                patcher = patcher.getParent();
                            }

                            Map<String, MCPFunction> preDecomps = Maps.newLinkedHashMap();
                            if (!ats.isEmpty() || AT_HASH != null) {
                                @SuppressWarnings("deprecation")
                                MCPFunction function = MCPFunctionFactory.createAT(project, MinecraftUserRepo.this.ATS, ats);
                                preDecomps.put("AccessTransformer", function);
                            }

                            if (!sas.isEmpty()) {
                                @SuppressWarnings("deprecation")
                                MCPFunction function = MCPFunctionFactory.createSAS(project, Collections.emptyList(), sas);
                                preDecomps.put("SideStripper", function);
                            }

                            ret = new MCPRuntime(project, data, getConfig(), side, dir, preDecomps);
                            runtimes.put(side, ret);
                        }
                        return ret;
                    }
                };
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public byte[] getData(String... path) throws IOException {
            return wrapper.getData(path);
        }

        public Artifact getArtifact() {
            return artifact;
        }

        public File getZip() {
            return wrapper.getZip();
        }

        public String getVersion() {
            return artifact.getVersion();
        }

        public String getMCVersion() {
            return wrapper.getConfig().getVersion();
        }

        public List<String> getLibraries() {
            return wrapper.getConfig().getLibraries("joined");
        }

        public File getStepOutput(String side, String step) throws IOException {
            MCPRuntime runtime = wrapper.getRuntime(project, side);
            try {
                return runtime.execute(log, step);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                log.lifecycle(e.getMessage());
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new RuntimeException(e);
            }
        }
    }
}
