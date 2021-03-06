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

package net.minecraftforge.gradle.mcp;

import net.minecraftforge.gradle.common.FGBasePlugin;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.task.SetupMCP;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class MCPPlugin implements Plugin<Project> {

    public static final String DOWNLOAD_MCPCONFIG_TASK_NAME = "downloadConfig";
    public static final String SETUP_MCP_TASK_NAME = "setupMCP";

    @Override
    public void apply(@Nonnull Project project) {
        project.getPlugins().apply(FGBasePlugin.class);
        MCPExtension extension = project.getExtensions().create(MCPExtension.EXTENSION_NAME, MCPExtension.class, project);

        TaskProvider<DownloadMCPConfig> downloadConfig = project.getTasks().register(DOWNLOAD_MCPCONFIG_TASK_NAME, DownloadMCPConfig.class);
        TaskProvider<SetupMCP> setupMCP = project.getTasks().register(SETUP_MCP_TASK_NAME, SetupMCP.class);

        downloadConfig.configure(task -> {
            task.getConfig().set(extension.getConfig().map(Artifact::getDescriptor));
            task.getOutput().set(project.file("build/mcp_config.zip"));
        });
        setupMCP.configure(task -> {
            task.dependsOn(downloadConfig);
            task.getPipeline().set(extension.getPipeline());
            task.getConfig().set(downloadConfig.flatMap(DownloadMCPConfig::getOutput));
        });
    }
}
