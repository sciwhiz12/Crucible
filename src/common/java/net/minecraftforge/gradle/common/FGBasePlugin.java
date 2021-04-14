package net.minecraftforge.gradle.common;

import net.minecraftforge.gradle.common.util.EnvironmentChecks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import net.minecraftforge.gradle.common.util.Utils;

public class FGBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {

        EnvironmentChecks.checkEnvironment(project);

        // Add known repos: Forge, Mojang, Maven Central

        project.getRepositories().maven(e -> {
            e.setUrl(Utils.FORGE_MAVEN);
            e.metadataSources(m -> {
                m.gradleMetadata();
                m.mavenPom();
                m.artifact();
            });
        });

        project.getRepositories().maven(e -> {
            e.setUrl(Utils.MOJANG_MAVEN);
            e.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
        });

        project.getRepositories().mavenCentral();
    }
}
