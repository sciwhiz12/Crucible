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

package net.minecraftforge.gradle.common.util;

import javax.inject.Inject;
import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public abstract class MinecraftExtension extends GroovyObjectSupport {

    protected final Project project;
    protected final NamedDomainObjectContainer<RunConfig> runs;

    protected final Property<String> mappingChannel;
    protected final Property<String> mappingVersion;
    protected final ConfigurableFileCollection accessTransformers;
    protected final ConfigurableFileCollection sideAnnotationStrippers;

    @Inject
    public MinecraftExtension(final Project project) {
        this.project = project;
        final ObjectFactory objects = project.getObjects();

        mappingChannel = objects.property(String.class);
        mappingVersion = objects.property(String.class);
        accessTransformers = objects.fileCollection();
        sideAnnotationStrippers = objects.fileCollection();

        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));
    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<RunConfig> runs(@SuppressWarnings("rawtypes") Closure closure) {
        return runs.configure(closure);
    }

    public NamedDomainObjectContainer<RunConfig> getRuns() {
        return runs;
    }

    public void propertyMissing(String name, Object value) {
        if (!(value instanceof Closure)) {
            throw new MissingPropertyException(name);
        }

        @SuppressWarnings("rawtypes") final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    @Deprecated  // TODO: Remove when we can break things.
    public void setMappings(String mappings) {
        project.getLogger().warn("Deprecated MinecraftExtension.setMappings called. Use mappings(channel, version)");
        int idx = mappings.lastIndexOf('_');
        if (idx == -1)
            throw new RuntimeException("Invalid mapping string format, must be {channel}_{version}. Consider using mappings(channel, version) directly.");
        String channel = mappings.substring(0, idx);
        String version = mappings.substring(idx + 1);
        mappings(channel, version);
    }

    public Property<String> getMappingChannel() {
        return this.mappingChannel;
    }

    public Property<String> getMappingVersion() {
        return this.mappingVersion;
    }

    public Provider<String> getMappings() {
        return this.mappingChannel.zip(this.mappingVersion, (ch, ver) -> ch + '_' + ver);
    }

    public void mappings(Provider<String> channel, Provider<String> version) {
        this.mappingChannel.set(channel);
        this.mappingVersion.set(version);
    }

    public void mappings(String channel, String version) {
        this.mappingChannel.set(channel);
        this.mappingVersion.set(version);
    }

    public void mappings(Map<String, ? extends CharSequence> mappings) {
        CharSequence channel = mappings.get("channel");
        CharSequence version = mappings.get("version");

        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify both mappings channel and version");
        }

        mappings(channel.toString(), version.toString());
    }

    public ConfigurableFileCollection getAccessTransformers() {
        return this.accessTransformers;
    }

    public ConfigurableFileCollection getSideAnnotationStrippers() {
        return this.sideAnnotationStrippers;
    }
}
