package net.minecraftforge.gradle.shim;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;

public class UserDevPluginShim implements Plugin<Project> {
    @Override
    public void apply(@Nonnull Project project) {
        ShimHelper.checkEnvironment();
        ShimHelper.applyPlugin(project, "net.minecraftforge.gradle.userdev.UserDevPlugin", "Userdev plugin");
    }
}
