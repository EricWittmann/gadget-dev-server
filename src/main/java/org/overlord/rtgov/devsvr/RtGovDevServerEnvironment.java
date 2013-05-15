/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.rtgov.devsvr;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.gadgets.web.server.StoreController;
import org.overlord.sramp.atom.archive.ArchiveUtils;

/**
 * Holds information about the RTGov development runtime environment.
 * @author eric.wittmann@redhat.com
 */
public class RtGovDevServerEnvironment {

    /**
     * Determine the current runtime environment.
     * @param args
     */
    public static RtGovDevServerEnvironment discover(String[] args) {
        final RtGovDevServerEnvironment environment = new RtGovDevServerEnvironment(args);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                environment.onVmExit();
            }
        }));
        return environment;
    }

    private boolean ide_gadgetServer = false;
    private boolean ide_gadgetWeb = false;
    private boolean ide_gadgets = false;
    private boolean ide_overlordHeader = false;
    private boolean usingClassHiderAgent = false;

    private File targetDir;
    private File gadgetServerWebAppDir;
    private File gadgetWebWebAppDir;
    private File gadgetsWebAppDir;
    private File overlordHeaderDir;

    private File gadgetServerWorkDir = null;
    private File gadgetWebWorkDir = null;
    private File gadgetsWorkDir = null;
    private File overlordCommonsWorkDir = null;

    /**
     * Constructor.
     * @param args
     */
    private RtGovDevServerEnvironment(String[] args) {
        findTargetDir();
        findGadgetServerWebAppDir();
        findGadgetWebWebAppDir();
        findGadgetsWebAppDir();
        findOverlordCommonsDir();
        inspectArgs(args);
        detectAgent();
    }

    /**
     * Do any cleanup on exit.
     */
    protected void onVmExit() {
        cleanWorkDirs();
    }

    /**
     * @return the ide_overlordHeader
     */
    public boolean isIde_overlordHeader() {
        return ide_overlordHeader;
    }

    /**
     * @return the ide_gadgetServer
     */
    public boolean isIde_gadgetServer() {
        return ide_gadgetServer;
    }

    /**
     * @return the ide_gadgetWeb
     */
    public boolean isIde_gadgetWeb() {
        return ide_gadgetWeb;
    }

    /**
     * @return the ide_gadgets
     */
    public boolean isIde_gadgets() {
        return ide_gadgets;
    }

    /**
     * @return the targetDir
     */
    public File getTargetDir() {
        return targetDir;
    }

    /**
     * @return the gadgetServerWebAppDir
     */
    public File getGadgetServerWebAppDir() {
        return gadgetServerWebAppDir;
    }

    /**
     * @return the gadgetWebWebAppDir
     */
    public File getGadgetWebWebAppDir() {
        return gadgetWebWebAppDir;
    }

    /**
     * @return the gadgetsWebAppDir
     */
    public File getGadgetsWebAppDir() {
        return gadgetsWebAppDir;
    }

    /**
     * @return the overlordHeaderDir
     */
    public File getOverlordHeaderDir() {
        return overlordHeaderDir;
    }

    /**
     * @return the maven target dir
     */
    private void findTargetDir() {
        String path = RtGovJettyDevServer.class.getClassLoader()
                .getResource(RtGovJettyDevServer.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find target directory.");
        }
        if (path.contains("/target/")) {
            path = path.substring(0, path.indexOf("/target/")) + "/target";
            targetDir = new File(path);
            System.out.println("Detected runtime 'target' directory: " + targetDir);
        } else {
            throw new RuntimeException("Failed to find target directory.");
        }
    }

    /**
     * Finds the gadget-server web app.
     */
    private void findGadgetServerWebAppDir() {
        findWebAppDir("gadget-server", "org.overlord.gadgets.server", "gadget-server", "ide_gadgetWeb", "gadgetServerWebAppDir",
                "gadgetServerWorkDir", false);
    }

    /**
     * Finds the gadgets.war web app.
     */
    private void findGadgetsWebAppDir() {
        findWebAppDir("gadgets", "org.overlord.gadgets.server", "gadgets", "ide_gadgetWeb", "gadgetsWebAppDir",
                "gadgetsWorkDir", true);
    }

    /**
     * Finds the gadget-web.war web app.
     */
    private void findGadgetWebWebAppDir() {
        findWebAppDir("gadget-web", StoreController.class, "ide_gadgetWeb", "gadgetWebWebAppDir",
                "gadgetWebWorkDir");
    }

    /**
     * Finds the web app dir for the given maven project.
     * @param webAppName
     * @param groupId
     * @param artifactId
     * @param ideBooleanFieldName
     * @param webAppDirFieldName
     * @param workDirFieldName
     * @param acceptIdePath
     */
    private void findWebAppDir(String webAppName, String groupId, String artifactId, String ideBooleanFieldName,
            String webAppDirFieldName, String workDirFieldName, boolean acceptIdePath) {
        try {
            findWebAppDir(webAppName, groupId, artifactId,
                    this.getClass().getDeclaredField(ideBooleanFieldName),
                    this.getClass().getDeclaredField(webAppDirFieldName),
                    this.getClass().getDeclaredField(workDirFieldName),
                    acceptIdePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds a webapp directory.
     * @param webAppName
     * @param webAppClass
     * @param ideBooleanFieldName
     * @param webAppDirFieldName
     * @param workDirFieldName
     */
    private void findWebAppDir(String webAppName, Class<?> webAppClass, String ideBooleanFieldName,
            String webAppDirFieldName, String workDirFieldName) {
        try {
            findWebAppDir(webAppName, webAppClass,
                    this.getClass().getDeclaredField(ideBooleanFieldName),
                    this.getClass().getDeclaredField(webAppDirFieldName),
                    this.getClass().getDeclaredField(workDirFieldName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds a web app on the classpath.
     * @param webAppName
     * @param webAppClass
     * @param ideBooleanField
     * @param webAppDirField
     * @param workDirField
     */
    private void findWebAppDir(String webAppName, Class<?> webAppClass, Field ideBooleanField,
            Field webAppDirField, Field workDirField) {

        System.out.println("\nSearching for web app: " + webAppName);
        System.out.println("------------------------------------------");

        String path = webAppClass.getClassLoader()
                .getResource(webAppClass.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find WAR for webapp: " + webAppName);
        }
        File file = new File(path);
        // The class file is available on the file system.
        try {
            if (file.exists()) {
                System.out.println("Detected " + webAppName + " classes on the filesystem.");
                System.out.println("\tAssumption: " + webAppName + " is imported into your IDE");

                ideBooleanField.set(this, true);
                if (path.contains("/WEB-INF/classes/")) {
                    String pathToWebApp = path.substring(0, path.indexOf("/WEB-INF/classes/"));
                    File webApp = new File(pathToWebApp);
                    webAppDirField.set(this, webApp);
                    System.out.println("Detected " + webAppName + " web app: " + webApp);
                    System.out.println("------------------------------------------\n");
                    return;
                } else {
                    throw new RuntimeException("Failed to find web app: " + webAppName);
                }
            } else {
                System.out.println("Detected " + webAppName + " classes in JAR.");
                System.out.println("\tAssumption: not running from IDE or " + webAppName + " project not imported");
                ideBooleanField.set(this, false);
                if (path.contains("-classes.jar") && path.startsWith("file:")) {
                    String pathToWar = path.substring(5, path.indexOf("-classes.jar")) + ".war";
                    File war = new File(pathToWar);
                    if (war.isFile()) {
                        System.out.println("Discovered " + webAppName + " WAR: " + war);
                        File workDir = new File(this.targetDir, webAppName.replace(' ', '_'));
                        workDirField.set(this, workDir);
                        cleanWorkDir(workDir);
                        workDir.mkdirs();
                        System.out.println("Unpacking " + webAppName + " war to: " + workDir);
                        ArchiveUtils.unpackToWorkDir(war, workDir);
                        // TODO provide filters here in case resources should be deleted
                        FileUtils.deleteDirectory(new File(workDir, "WEB-INF/lib"));
                        webAppDirField.set(this, workDir);
                        System.out.println("Detected " + webAppName + " web app: " + workDir);
                        System.out.println("------------------------------------------\n");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find " + webAppName + " webapp directory.", e);
        }

        throw new RuntimeException("Failed to find " + webAppName + " webapp directory.");
    }

    /**
     * Finds a web app on the classpath by looking for its maven artifactId and groupId.
     * @param webAppName
     * @param groupId
     * @param artifactId
     * @param ideBooleanField
     * @param webAppDirField
     * @param workDirField
     * @param acceptIdePath
     */
    private void findWebAppDir(String webAppName, String groupId, String artifactId, Field ideBooleanField,
            Field webAppDirField, Field workDirField, boolean acceptIdePath) {

        System.out.println("\nSearching for web app: " + webAppName);
        System.out.println("------------------------------------------");
        URLClassLoader urlCL = (URLClassLoader) RtGovJettyDevServer.class.getClassLoader();
        TreeSet<URL> sortedURLs = new TreeSet<URL>(new Comparator<URL>() {
            @Override
            public int compare(URL o1, URL o2) {
                return o1.toExternalForm().compareTo(o2.toExternalForm());
            }
        });
        sortedURLs.addAll(Arrays.asList(urlCL.getURLs()));

        boolean ideUrl = false;
        boolean mavenUrl = false;
        String moduleUrl = null;

        // First, look for something that looks like an IDE path (if required)
        for (URL url : sortedURLs) {
            String urlstr = url.toExternalForm();
            if (urlstr.contains("/"+artifactId+"/target")) {
                moduleUrl = urlstr;
                ideUrl = true;
                break;
            }
        }
        // If not found, look for something that looks like a maven path
        if (!ideUrl) {
            String groupIdAsPath = groupId.replace('.', '/');
            for (URL url : sortedURLs) {
                String urlstr = url.toExternalForm();
                if (urlstr.contains(groupIdAsPath) && urlstr.contains("/"+artifactId+"-")) {
                    moduleUrl = urlstr;
                    mavenUrl = true;
                    break;
                }
            }
        }

        try {
            if (ideUrl) {
                System.out.println("Detected " + webAppName + " web app on the filesystem.");
                System.out.println("\tAssumption: " + webAppName + " is imported into your IDE");

                ideBooleanField.set(this, true);
                String pathToWebApp = null;
                if (acceptIdePath) {
                    pathToWebApp = moduleUrl.replace("/target/classes", "/src/main/webapp");
                } else {
                    pathToWebApp = moduleUrl.replace("/target/classes", "/target/" + artifactId);
                }

                File webApp = new File(new URL(pathToWebApp).toURI());
                webAppDirField.set(this, webApp);
                System.out.println("Detected " + webAppName + " web app: " + webApp);
                System.out.println("------------------------------------------\n");
                return;
            } else if (mavenUrl) {
                System.out.println("Detected " + webAppName + " -classes.jar.");
                System.out.println("\tAssumption: not running from IDE or " + webAppName + " project not imported");
                ideBooleanField.set(this, false);
                String pathToWar = moduleUrl.replace("-classes.jar", ".war");

                URL warUrl = new URL(pathToWar);
                File war = new File(warUrl.toURI());
                System.out.println("Path to war: " + war);
                if (war.isFile()) {
                    System.out.println("Discovered " + webAppName + " WAR: " + war);
                    File workDir = new File(this.targetDir, webAppName.replace(' ', '_'));
                    workDirField.set(this, workDir);
                    cleanWorkDir(workDir);
                    workDir.mkdirs();
                    System.out.println("Unpacking " + webAppName + " war to: " + workDir);
                    ArchiveUtils.unpackToWorkDir(war, workDir);
                    webAppDirField.set(this, workDir);
                    System.out.println("Detected " + webAppName + " web app: " + workDir);
                    System.out.println("------------------------------------------\n");
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find " + webAppName + " webapp directory.", e);
        }
        throw new RuntimeException("Failed to find " + webAppName + " webapp directory.");

    }


    /**
     * Attempts to find the directory containing the Overlord Commons shared
     * header javascript and CSS files.  If running in an IDE, and if the
     * overlord-commons project is imported, then a file path directly to those
     * files should get returned.  If running from maven or if the overlord-commons
     * project is not imported into the IDE, then the overlord-commons JAR will
     * be unpacked to a temporary directory.  This temp directory will get used
     * instead.
     */
    private void findOverlordCommonsDir() {
        String path = OverlordHeaderDataJS.class.getClassLoader()
                .getResource(OverlordHeaderDataJS.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find Overlord Commons classes.");
        }
        File file = new File(path);
        // The class file is available on the file system.
        if (file.exists()) {
            System.out.println("Detected Overlord Commons Header UI classes on the filesystem.");
            System.out.println("\tAssumption: overlord-commons is imported into your IDE.");
            this.ide_overlordHeader = true;
            if (path.contains("/target/classes/")) {
                String pathToProj = path.substring(0, path.indexOf("/target/classes/"));
                this.overlordHeaderDir = new File(pathToProj, "src/main/resources/META-INF/resources");
                if (!this.overlordHeaderDir.isDirectory()) {
                    throw new RuntimeException("Missing directory: " + this.overlordHeaderDir);
                }
                System.out.println("Detected Overlord Header UI path: " + overlordHeaderDir);
                return;
            } else {
                throw new RuntimeException("Failed to find Overlord Header UI files.");
            }
        } else {
            System.out.println("Detected Overlord Commons Header UI classes in JAR.");
            System.out.println("\tAssumption: running from Maven or overlord-commons not imported in IDE.");
            this.ide_overlordHeader = false;
            if (path.contains(".jar") && path.startsWith("file:")) {
                String pathToJar = path.substring(5, path.indexOf(".jar")) + ".jar";
                File jar = new File(pathToJar);
                if (jar.isFile()) {
                    System.out.println("Discovered Overlord Commons UI jar: " + jar);
                    this.overlordCommonsWorkDir = new File(this.targetDir, "overlord-commons-uiheader");
                    cleanWorkDir(overlordCommonsWorkDir);
                    this.overlordCommonsWorkDir.mkdirs();
                    try {
                        System.out.println("Unpacking Overlord Commons UI jar to: " + overlordCommonsWorkDir);
                        ArchiveUtils.unpackToWorkDir(jar, overlordCommonsWorkDir);
                        this.overlordHeaderDir = new File(overlordCommonsWorkDir, "META-INF/resources");
                        System.out.println("Detected Overlord Commons Header UI path: " + overlordHeaderDir);
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to find S-RAMP UI webapp directory.");
    }

    /**
     * Clean the various work directories.
     */
    private void cleanWorkDirs() {
        cleanWorkDir(gadgetServerWorkDir);
        cleanWorkDir(gadgetWebWorkDir);
        cleanWorkDir(gadgetsWorkDir);
        cleanWorkDir(overlordCommonsWorkDir);
    }

    /**
     * Clean the given work dir.
     */
    private void cleanWorkDir(File workDir) {
        if (workDir != null) {
            try { FileUtils.deleteDirectory(workDir); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * @return the usingClassHiderAgent
     */
    public boolean isUsingClassHiderAgent() {
        return usingClassHiderAgent;
    }

    /**
     * Checks for interesting command line args.
     * @param args
     */
    private void inspectArgs(String[] args) {
    }

    /**
     * Checks for the existence of the java agent.
     */
    private void detectAgent() {
        try {
            Class.forName("org.jboss.errai.ClientLocalClassHidingAgent");
            this.usingClassHiderAgent = true;
        } catch (ClassNotFoundException e) {
            this.usingClassHiderAgent = false;
        }
    }

    /**
     * Creates the UI application configs and sets the system property telling the Overlord
     * Header servlet where to find them.
     * @throws Exception
     */
    public void createAppConfigs() throws Exception {
        File dir = new File(this.targetDir, "overlord-apps");
        if (dir.isDirectory()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();

        File configFile1 = new File(dir, "srampui-overlordapp.properties");
        Properties props = new Properties();
        props.setProperty("overlordapp.app-id", "s-ramp-ui");
        props.setProperty("overlordapp.href", "/s-ramp-ui/index.html?gwt.codesvr=127.0.0.1:9997");
        props.setProperty("overlordapp.label", "S-RAMP");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "S-RAMP Repository");
        props.store(new FileWriter(configFile1), "S-RAMP UI application");

        File configFile2 = new File(dir, "dtgov-overlordapp.properties");
        props = new Properties();
        props.setProperty("overlordapp.app-id", "dtgov");
        props.setProperty("overlordapp.href", "/dtgov/index.html?gwt.codesvr=127.0.0.1:9997");
        props.setProperty("overlordapp.label", "DTGov");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "Design Time Governance");
        props.store(new FileWriter(configFile2), "DTGov UI application");

        File configFile3 = new File(dir, "gadgets-overlordapp.properties");
        props = new Properties();
        props.setProperty("overlordapp.app-id", "gadgets");
        props.setProperty("overlordapp.href", "/gadgets/");
        props.setProperty("overlordapp.label", "Gadget Server");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "Gadget Server");
        props.store(new FileWriter(configFile3), "Gadget Server UI application");

        System.setProperty("org.overlord.apps.config-dir", dir.getCanonicalPath());
        System.out.println("Generated app configs in: " + dir.getCanonicalPath());
    }
}
