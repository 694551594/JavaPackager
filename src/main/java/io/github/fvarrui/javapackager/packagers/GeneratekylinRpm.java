package io.github.fvarrui.javapackager.packagers;

import io.github.fvarrui.javapackager.utils.FileUtils;
import io.github.fvarrui.javapackager.utils.Logger;
import io.github.fvarrui.javapackager.utils.VelocityUtils;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.io.FilenameUtils;
import org.redline_rpm.Builder;
import org.redline_rpm.header.Architecture;
import org.redline_rpm.header.Os;
import org.redline_rpm.header.RpmType;
import org.vafer.jdeb.Console;
import org.vafer.jdeb.DataProducer;
import org.vafer.jdeb.DebMaker;
import org.vafer.jdeb.ant.Data;
import org.vafer.jdeb.ant.Mapper;
import org.vafer.jdeb.mapping.PermMapper;
import org.vafer.jdeb.producers.DataProducerLink;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class GeneratekylinRpm extends ArtifactGenerator<LinuxPackager> {

    private Console console;

    public GeneratekylinRpm() {
        super("Kylin Rpm package");
        console = new Console() {

            @Override
            public void warn(String message) {
                Logger.warn(message);
            }

            @Override
            public void info(String message) {
                Logger.info(message);
            }

            @Override
            public void debug(String message) {
                Logger.debug(message);
            }

        };
    }

    @Override
    public boolean skip(LinuxPackager packager) {
        return !packager.getLinuxConfig().isGenerateKylinRpm();
    }

    @Override
    protected File doApply(LinuxPackager packager) throws Exception {
        String arch = packager.getArch();
        File assetsFolder = packager.getAssetsFolder();
        String name = packager.getName();
        File appFolder = packager.getAppFolder();
        String description = packager.getDescription();
        String organizationName = packager.getOrganizationName();
        File outputDirectory = packager.getOutputDirectory();
        String version = packager.getVersion();
        boolean bundleJre = packager.getBundleJre();
        String jreDirectoryName = packager.getJreDirectoryName();
        File executable = packager.getExecutable();
        File javaFile = new File(appFolder, jreDirectoryName + "/bin/java");

        // generates desktop file from velocity template
        File desktopFile = new File(assetsFolder, name + ".desktop");
        VelocityUtils.render("linux/" + packager.getLinuxConfig().getDesktopFile(), desktopFile, packager);
        Logger.info("Desktop file rendered in " + desktopFile.getAbsolutePath());
        FileUtils.copyFileToFolder(desktopFile, appFolder);

        // generates deb control file from velocity template
        File controlFile = new File(assetsFolder, "control");
        VelocityUtils.render("linux/" + packager.getLinuxConfig().getControlFile(), controlFile, packager);
        Logger.info("Control file rendered in " + controlFile.getAbsolutePath());

        // generated deb file
        File rpmFile = new File(outputDirectory, name + "_" + version + "_" + arch + ".rpm");

        // create data producers collections

        File proguardFolder = new File(appFolder.getParentFile().getParentFile(), "outlibs");
        File[] proguardFiles = proguardFolder.listFiles();
        File libsFolder = new File(appFolder.getParentFile(), "libs");
        for (File proguardFile : proguardFiles) {
            FileUtils.copyFileToFolder(proguardFile, libsFolder);
        }
        File appLibsFolder = new File(appFolder, "libs");
        appLibsFolder.mkdirs();
        File[] libFiles = libsFolder.listFiles();
        for (File libFile : libFiles) {
            if (!arch.equals("win") && FilenameUtils.getBaseName(libFile.getName()).endsWith("-win")) {
                continue;
            }
            if (!arch.equals("amd64") && FilenameUtils.getBaseName(libFile.getName()).endsWith("-linux")) {
                continue;
            }
            if (!arch.equals("arm64") && FilenameUtils.getBaseName(libFile.getName()).endsWith("-linux-aarch64")) {
                continue;
            }
            FileUtils.copyFileToFolder(libFile, appLibsFolder);
        }

        Builder builder = new Builder();
        builder.setType(RpmType.BINARY);
        builder.setPlatform(Architecture.X86_64, Os.LINUX);
        builder.setPackage(name, version, "1");
        builder.setPackager(organizationName);
        builder.setDescription(description);
        builder.setPrefixes("opt");

        // list of files which needs execution permissions
        List<File> executionPermissions = new ArrayList<>();
        executionPermissions.add(executable);
        executionPermissions.add(new File(appFolder, jreDirectoryName + "/bin/java"));
        executionPermissions.add(new File(appFolder, jreDirectoryName + "/lib/jspawnhelper"));

        // add all app files
        addDirectoryTree(builder, "/opt/apps", appFolder, executionPermissions);

        // link to desktop file
        builder.addLink("/usr/share/applications/" + desktopFile.getName(), "/opt/apps/" + name + "/" + desktopFile.getName());

        // link to binary
        builder.addLink("/usr/local/bin/" + executable.getName(), "/opt/apps/" + name + "/" + executable.getName());

        builder.build(outputDirectory);

        File originalRpm = new File(outputDirectory, name + "-" + version + "-1.x86_64.rpm");
        File rpm = null;
        if (originalRpm.exists()) {
            rpm = rpmFile;
            if (rpm.exists()) rpm.delete();
            FileUtils.rename(originalRpm, rpm.getName());
        }

        return rpm;

    }

    private void addDirectoryTree(Builder builder, String parentPath, File root, List<File> executionPermissions) throws NoSuchAlgorithmException, IOException {
        String rootPath = parentPath + "/" + root.getName();
        builder.addDirectory(rootPath);
        for (File f : root.listFiles()) {
            if (f.isDirectory())
                addDirectoryTree(builder, parentPath + "/" + root.getName(), f, executionPermissions);
            else {
                builder.addFile(rootPath + "/" + f.getName(), f, executionPermissions.contains(f) ? 0755 : 0644);
            }
        }
    }

}
