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
package org.overlord.gadgets.server.devsvr;

import org.overlord.commons.dev.server.DevServerEnvironment;

/**
 * Holds information about the gadget server development runtime environment.
 * @author eric.wittmann@redhat.com
 */
public class GadgetDevServerEnvironment extends DevServerEnvironment {

    /**
     * Constructor.
     * @param args
     */
    public GadgetDevServerEnvironment(String[] args) {
        super(args);
    }
//
//    /**
//     * Finds the gadget-server web app.
//     */
//    private void findGadgetServerWebAppDir() {
//        findWebAppDir("gadget-server", "org.overlord.gadgets.server", "gadget-server", "ide_gadgetWeb", "gadgetServerWebAppDir",
//                "gadgetServerWorkDir", false);
//    }
//
//    /**
//     * Finds the gadgets.war web app.
//     */
//    private void findGadgetsWebAppDir() {
//        findWebAppDir("gadgets", "org.overlord.gadgets.server", "gadgets", "ide_gadgetWeb", "gadgetsWebAppDir",
//                "gadgetsWorkDir", true);
//    }
//
//    /**
//     * Finds the gadget-web.war web app.
//     */
//    private void findGadgetWebWebAppDir() {
//        findWebAppDir("gadget-web", StoreController.class, "ide_gadgetWeb", "gadgetWebWebAppDir",
//                "gadgetWebWorkDir");
//    }
//
//    /**
//     * Finds the web app dir for the given maven project.
//     * @param webAppName
//     * @param groupId
//     * @param artifactId
//     * @param ideBooleanFieldName
//     * @param webAppDirFieldName
//     * @param workDirFieldName
//     * @param acceptIdePath
//     */
//    private void findWebAppDir(String webAppName, String groupId, String artifactId, String ideBooleanFieldName,
//            String webAppDirFieldName, String workDirFieldName, boolean acceptIdePath) {
//        try {
//            findWebAppDir(webAppName, groupId, artifactId,
//                    this.getClass().getDeclaredField(ideBooleanFieldName),
//                    this.getClass().getDeclaredField(webAppDirFieldName),
//                    this.getClass().getDeclaredField(workDirFieldName),
//                    acceptIdePath);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    /**
//     * Finds a webapp directory.
//     * @param webAppName
//     * @param webAppClass
//     * @param ideBooleanFieldName
//     * @param webAppDirFieldName
//     * @param workDirFieldName
//     */
//    private void findWebAppDir(String webAppName, Class<?> webAppClass, String ideBooleanFieldName,
//            String webAppDirFieldName, String workDirFieldName) {
//        try {
//            findWebAppDir(webAppName, webAppClass,
//                    this.getClass().getDeclaredField(ideBooleanFieldName),
//                    this.getClass().getDeclaredField(webAppDirFieldName),
//                    this.getClass().getDeclaredField(workDirFieldName));
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    /**
//     * Finds a web app on the classpath.
//     * @param webAppName
//     * @param webAppClass
//     * @param ideBooleanField
//     * @param webAppDirField
//     * @param workDirField
//     */
//    private void findWebAppDir(String webAppName, Class<?> webAppClass, Field ideBooleanField,
//            Field webAppDirField, Field workDirField) {
//
//        System.out.println("\nSearching for web app: " + webAppName);
//        System.out.println("------------------------------------------");
//
//        String path = webAppClass.getClassLoader()
//                .getResource(webAppClass.getName().replace('.', '/') + ".class").getPath();
//        if (path == null) {
//            throw new RuntimeException("Failed to find WAR for webapp: " + webAppName);
//        }
//        File file = new File(path);
//        // The class file is available on the file system.
//        try {
//            if (file.exists()) {
//                System.out.println("Detected " + webAppName + " classes on the filesystem.");
//                System.out.println("\tAssumption: " + webAppName + " is imported into your IDE");
//
//                ideBooleanField.set(this, true);
//                if (path.contains("/WEB-INF/classes/")) {
//                    String pathToWebApp = path.substring(0, path.indexOf("/WEB-INF/classes/"));
//                    File webApp = new File(pathToWebApp);
//                    webAppDirField.set(this, webApp);
//                    System.out.println("Detected " + webAppName + " web app: " + webApp);
//                    System.out.println("------------------------------------------\n");
//                    return;
//                } else {
//                    throw new RuntimeException("Failed to find web app: " + webAppName);
//                }
//            } else {
//                System.out.println("Detected " + webAppName + " classes in JAR.");
//                System.out.println("\tAssumption: not running from IDE or " + webAppName + " project not imported");
//                ideBooleanField.set(this, false);
//                if (path.contains("-classes.jar") && path.startsWith("file:")) {
//                    String pathToWar = path.substring(5, path.indexOf("-classes.jar")) + ".war";
//                    File war = new File(pathToWar);
//                    if (war.isFile()) {
//                        System.out.println("Discovered " + webAppName + " WAR: " + war);
//                        File workDir = new File(this.targetDir, webAppName.replace(' ', '_'));
//                        workDirField.set(this, workDir);
//                        cleanWorkDir(workDir);
//                        workDir.mkdirs();
//                        System.out.println("Unpacking " + webAppName + " war to: " + workDir);
//                        ArchiveUtils.unpackToWorkDir(war, workDir);
//                        // TODO provide filters here in case resources should be deleted
//                        FileUtils.deleteDirectory(new File(workDir, "WEB-INF/lib"));
//                        webAppDirField.set(this, workDir);
//                        System.out.println("Detected " + webAppName + " web app: " + workDir);
//                        System.out.println("------------------------------------------\n");
//                        return;
//                    }
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to find " + webAppName + " webapp directory.", e);
//        }
//
//        throw new RuntimeException("Failed to find " + webAppName + " webapp directory.");
//    }
//
//    /**
//     * Finds a web app on the classpath by looking for its maven artifactId and groupId.
//     * @param webAppName
//     * @param groupId
//     * @param artifactId
//     * @param ideBooleanField
//     * @param webAppDirField
//     * @param workDirField
//     * @param acceptIdePath
//     */
//    private void findWebAppDir(String webAppName, String groupId, String artifactId, Field ideBooleanField,
//            Field webAppDirField, Field workDirField, boolean acceptIdePath) {
//
//        System.out.println("\nSearching for web app: " + webAppName);
//        System.out.println("------------------------------------------");
//        URLClassLoader urlCL = (URLClassLoader) GadgetJettyDevServer.class.getClassLoader();
//        TreeSet<URL> sortedURLs = new TreeSet<URL>(new Comparator<URL>() {
//            @Override
//            public int compare(URL o1, URL o2) {
//                return o1.toExternalForm().compareTo(o2.toExternalForm());
//            }
//        });
//        sortedURLs.addAll(Arrays.asList(urlCL.getURLs()));
//
//        boolean ideUrl = false;
//        boolean mavenUrl = false;
//        String moduleUrl = null;
//
//        // First, look for something that looks like an IDE path (if required)
//        for (URL url : sortedURLs) {
//            String urlstr = url.toExternalForm();
//            if (urlstr.contains("/"+artifactId+"/target")) {
//                moduleUrl = urlstr;
//                ideUrl = true;
//                break;
//            }
//        }
//        // If not found, look for something that looks like a maven path
//        if (!ideUrl) {
//            String groupIdAsPath = groupId.replace('.', '/');
//            for (URL url : sortedURLs) {
//                String urlstr = url.toExternalForm();
//                if (urlstr.contains(groupIdAsPath) && urlstr.contains("/"+artifactId+"-")) {
//                    moduleUrl = urlstr;
//                    mavenUrl = true;
//                    break;
//                }
//            }
//        }
//
//        try {
//            if (ideUrl) {
//                System.out.println("Detected " + webAppName + " web app on the filesystem.");
//                System.out.println("\tAssumption: " + webAppName + " is imported into your IDE");
//
//                ideBooleanField.set(this, true);
//                String pathToWebApp = null;
//                if (acceptIdePath) {
//                    pathToWebApp = moduleUrl.replace("/target/classes", "/src/main/webapp");
//                } else {
//                    pathToWebApp = moduleUrl.replace("/target/classes", "/target/" + artifactId);
//                }
//
//                File webApp = new File(new URL(pathToWebApp).toURI());
//                webAppDirField.set(this, webApp);
//                System.out.println("Detected " + webAppName + " web app: " + webApp);
//                System.out.println("------------------------------------------\n");
//                return;
//            } else if (mavenUrl) {
//                System.out.println("Detected " + webAppName + " -classes.jar.");
//                System.out.println("\tAssumption: not running from IDE or " + webAppName + " project not imported");
//                ideBooleanField.set(this, false);
//                String pathToWar = moduleUrl.replace("-classes.jar", ".war");
//
//                URL warUrl = new URL(pathToWar);
//                File war = new File(warUrl.toURI());
//                System.out.println("Path to war: " + war);
//                if (war.isFile()) {
//                    System.out.println("Discovered " + webAppName + " WAR: " + war);
//                    File workDir = new File(this.targetDir, webAppName.replace(' ', '_'));
//                    workDirField.set(this, workDir);
//                    cleanWorkDir(workDir);
//                    workDir.mkdirs();
//                    System.out.println("Unpacking " + webAppName + " war to: " + workDir);
//                    ArchiveUtils.unpackToWorkDir(war, workDir);
//                    webAppDirField.set(this, workDir);
//                    System.out.println("Detected " + webAppName + " web app: " + workDir);
//                    System.out.println("------------------------------------------\n");
//                    return;
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to find " + webAppName + " webapp directory.", e);
//        }
//        throw new RuntimeException("Failed to find " + webAppName + " webapp directory.");
//
//    }

}
