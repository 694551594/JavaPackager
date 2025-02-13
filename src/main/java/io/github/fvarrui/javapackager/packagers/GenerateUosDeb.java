package io.github.fvarrui.javapackager.packagers;

import io.github.fvarrui.javapackager.utils.FileUtils;
import io.github.fvarrui.javapackager.utils.Logger;
import io.github.fvarrui.javapackager.utils.VelocityUtils;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.io.FilenameUtils;
import org.vafer.jdeb.Console;
import org.vafer.jdeb.DataProducer;
import org.vafer.jdeb.DebMaker;
import org.vafer.jdeb.ant.Data;
import org.vafer.jdeb.ant.Mapper;
import org.vafer.jdeb.mapping.PermMapper;
import org.vafer.jdeb.producers.DataProducerLink;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GenerateUosDeb extends ArtifactGenerator<LinuxPackager> {

    private Console console;

    public GenerateUosDeb() {
        super("UOS DEB package");
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
        return !packager.getLinuxConfig().isGenerateUosDeb();
    }

    @Override
    protected File doApply(LinuxPackager packager) throws Exception {
        String arch = packager.getArch();
        File assetsFolder = packager.getAssetsFolder();
        String name = packager.getName();
        File appFolder = packager.getAppFolder();
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

        // generates deb control file from velocity template
        File controlFileFolder = new File(assetsFolder, "control");
        controlFileFolder.mkdirs();
        File controlFile = new File(controlFileFolder, "control");
        VelocityUtils.render("linux/" + packager.getLinuxConfig().getControlFile(), controlFile, packager);
        Logger.info("Control file rendered in " + controlFile.getAbsolutePath());

        // generated deb file
        File debFile = new File(outputDirectory, name + "_" + version + "_" + arch + ".deb");

        // create data producers collections

        List<DataProducer> conffilesProducers = new ArrayList<>();
        List<DataProducer> dataProducers = new ArrayList<>();

        // builds app folder data producer, except executable file and jre/bin/java

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

        Mapper appFolderMapper = new Mapper();
        appFolderMapper.setType("perm");
        appFolderMapper.setPrefix("/opt/apps/" + name + "/files");
        appFolderMapper.setFileMode("644");

        Data appFolderData = new Data();
        appFolderData.setType("directory");
        appFolderData.setSrc(appFolder);
        appFolderData.setExcludes(executable.getName() + (bundleJre ? "," + jreDirectoryName + "/bin/java" + "," + jreDirectoryName + "/lib/jspawnhelper" : ""));
        appFolderData.addMapper(appFolderMapper);

        dataProducers.add(appFolderData);

        Mapper infoMapper = new Mapper();
        infoMapper.setType("perm");
        infoMapper.setPrefix("/opt/apps/" + name + "");
        infoMapper.setFileMode("755");

        Data infoData = new Data();
        infoData.setType("file");
        File infoFile = new File(assetsFolder, "linux/info");
        FileUtils.copyFileToFile(new File(packager.getAssetsDir(), "linux/" + packager.getLinuxConfig().getInfoFile()), infoFile);
        infoData.setSrc(infoFile);
        infoData.addMapper(infoMapper);

        dataProducers.add(infoData);

        // builds executable data producer

        Mapper executableMapper = new Mapper();
        executableMapper.setType("perm");
        executableMapper.setPrefix("/opt/apps/" + name + "/files");
        executableMapper.setFileMode("755");

        Data executableData = new Data();
        executableData.setType("file");
        executableData.setSrc(new File(appFolder.getAbsolutePath() + "/" + name));
        executableData.addMapper(executableMapper);

        dataProducers.add(executableData);

        // desktop file data producer

        Mapper desktopFileMapper = new Mapper();
        desktopFileMapper.setType("perm");
        desktopFileMapper.setPrefix("/opt/apps/" + name + "/entries/applications");

        Data desktopFileData = new Data();
        desktopFileData.setType("file");
        desktopFileData.setSrc(desktopFile);
        desktopFileData.addMapper(desktopFileMapper);

        dataProducers.add(desktopFileData);

        // java binary file data producer

        if (bundleJre) {

            Mapper javaBinaryMapper = new Mapper();
            javaBinaryMapper.setType("perm");
            javaBinaryMapper.setFileMode("755");
            javaBinaryMapper.setPrefix("/opt/apps/" + name + "/files/" + jreDirectoryName + "/bin");

            Data javaBinaryData = new Data();
            javaBinaryData.setType("file");
            javaBinaryData.setSrc(javaFile);
            javaBinaryData.addMapper(javaBinaryMapper);

            dataProducers.add(javaBinaryData);

            // set correct permissions on jre/lib/jspawnhelper
            Mapper javaSpawnHelperMapper = new Mapper();
            javaSpawnHelperMapper.setType("perm");
            javaSpawnHelperMapper.setFileMode("755");
            javaSpawnHelperMapper.setPrefix("/opt/apps/" + name + "/files/" + jreDirectoryName + "/lib");

            File jSpawnHelperFile = new File(appFolder, jreDirectoryName + "/lib/jspawnhelper");

            Data javaSpawnHelperData = new Data();
            javaSpawnHelperData.setType("file");
            javaSpawnHelperData.setSrc(jSpawnHelperFile);
            javaSpawnHelperData.addMapper(javaSpawnHelperMapper);

            dataProducers.add(javaSpawnHelperData);

        }

        // symbolic link in /usr/local/bin to app binary data producer

        // DataProducer linkData = createLink("/usr/local/bin/" + name, "/opt/" + name + "/" + name);

        // dataProducers.add(linkData);

        Data iconData = new Data();
        iconData.setType("file");
        iconData.setSrc(packager.getIconFile());

        Mapper iconMapper = new Mapper();
        iconMapper.setType("perm");
        iconMapper.setPrefix("/opt/apps/" + name + "/entries/icons");
        iconMapper.setFileMode("755");
        iconData.addMapper(iconMapper);
        dataProducers.add(iconData);

        dataProducers.add(this.createFolder("/opt/apps/" + name + "/entries/mime"));
        dataProducers.add(this.createFolder("/opt/apps/" + name + "/entries/plugins"));
        dataProducers.add(this.createFolder("/opt/apps/" + name + "/entries/services"));
        // builds deb file

        DebMaker debMaker = new DebMaker(console, dataProducers, conffilesProducers);
        debMaker.setDeb(debFile);
        debMaker.setControl(controlFile.getParentFile());
        debMaker.setCompression("gzip");
        debMaker.setDigest("SHA256");
        debMaker.validate();
        debMaker.makeDeb();

        return debFile;

    }

    private DataProducer createFolder(String target) {
        File file = new File(target);
        file.mkdirs();
        Data folderData = new Data();
        folderData.setType("directory");
        folderData.setSrc(file);

        Mapper folderMapper = new Mapper();
        folderMapper.setType("perm");
        folderMapper.setFileMode("755");
        folderMapper.setPrefix(target);
        folderData.addMapper(folderMapper);

        return folderData;
    }

    private DataProducer createLink(String name, String target) {
        int linkMode = UnixStat.LINK_FLAG | Integer.parseInt("777", 8);
        org.vafer.jdeb.mapping.Mapper linkMapper = new PermMapper(
                0, 0,                    // uid, gid
                "root", "root",        // user, group
                linkMode, linkMode,    // perms
                0, null
        );
        return new DataProducerLink(
                name,        // link name
                target,    // target
                true,        // symbolic link
                null, null,
                new org.vafer.jdeb.mapping.Mapper[]{linkMapper}    // link mapper
        );
    }

}

