package net.minecraftforge.gradle.shim;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

class ShimHelper {
    public static void checkEnvironment() {
        if (JavaVersion.current().compareTo(JavaVersion.VERSION_16) < 0) {
            throw new IllegalStateException("Detected version " + JavaVersion.current().toString()
                    + "; [Crucible] is built for Java 16 and above. Please update your java version");
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyPlugin(Project project, String clz, String name) {
        try {
            project.getPlugins().apply((Class<? extends Plugin<?>>) Class.forName(clz));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to resolve " + name, e);
        }
    }
}
