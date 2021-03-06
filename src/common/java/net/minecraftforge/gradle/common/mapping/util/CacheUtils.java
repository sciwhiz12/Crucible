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

package net.minecraftforge.gradle.common.mapping.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.gradle.api.Project;
import net.minecraftforge.gradle.common.config.MCPConfigV2;
import net.minecraftforge.gradle.common.mapping.detail.IMappingDetail;
import net.minecraftforge.gradle.common.mapping.generator.MappingZipGenerator;
import net.minecraftforge.gradle.common.mapping.info.IMappingInfo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.func.IOSupplier;

import net.minecraftforge.srgutils.IMappingFile;

/**
 * Utility Class handling caching
 */
public class CacheUtils {

    public static IMappingInfo fromCacheable(String channel, String version, HashStore cache, File destination, IOSupplier<IMappingDetail> supplier) throws IOException {
        if (!cache.isSame() || !destination.exists()) {
            IMappingDetail detail = supplier.get();

            MappingZipGenerator.generate(destination, detail);

            cache.save();
            Utils.updateHash(destination, HashFunction.SHA1);

            return IMappingInfo.of(channel, version, destination, detail);
        }

        return IMappingInfo.of(channel, version, destination);
    }

    public static File findRenames(Project project, String classifier, IMappingFile.Format format, String version, boolean toObf) throws IOException {
        String ext = format.name().toLowerCase();
        File mcp = getMCPConfigZip(project, version);
        if (mcp == null)
            return null;

        File file = cacheMCP(project, version, classifier, ext);
        HashStore cache = commonHash(project, mcp).load(cacheMCP(project, version, classifier, ext + ".input"));

        if (!cache.isSame() || !file.exists()) {
            String name = MCPConfigV2.getFromArchive(mcp).getData("mappings");
            byte[] data = Utils.getZipData(mcp, name);
            IMappingFile obf_to_srg = IMappingFile.load(new ByteArrayInputStream(data));
            obf_to_srg.write(file.toPath(), format, toObf);
            cache.save();
            Utils.updateHash(file, HashFunction.SHA1);
        }

        return file;
    }

    public static File getMCPConfigZip(Project project, String version) {
        return MavenArtifactDownloader.manual(project, "de.oceanlabs.mcp:mcp_config:" + version + "@zip", false);
    }

    public static File cacheMappings(Project project, String channel, String version, String ext) {
        return cacheMC(project, Objects.equals(channel, "official") ? "mapping" : "mappings_" + channel, version, "mapping", ext);
    }

    public static File cacheMC(Project project, String side, String version, String classifier, String ext) {
        if (classifier != null)
            return cache(project, "net", "minecraft", side, version, side + '-' + version + '-' + classifier + '.' + ext);
        return cache(project, "net", "minecraft", side, version, side + '-' + version + '.' + ext);
    }

    public static File cacheMCP(Project project, String version, String classifier, String ext) {
        if (classifier != null)
            return cache(project, "de", "oceanlabs", "mcp", "mcp_config", version, "mcp_config-" + version + '-' + classifier + '.' + ext);
        return cache(project, "de", "oceanlabs", "mcp", "mcp_config", version, "mcp_config-" + version + '.' + ext);
    }

    public static File cacheMCP(Project project, String version) {
        return cache(project, "de", "oceanlabs", "mcp", "mcp_config", version);
    }

    public static File cache(Project project, String... path) {
        return new File(cacheRoot(project), String.join(File.separator, path));
    }

    public static HashStore commonHash(Project project) {
        return new HashStore(cacheRoot(project));
    }

    public static HashStore commonHash(Project project, File mcp) {
        return commonHash(project).add("mcp", mcp);
    }

    public static File cacheRoot(Project project) {
        return Utils.getCache(project, "mcp_repo");
    }

}