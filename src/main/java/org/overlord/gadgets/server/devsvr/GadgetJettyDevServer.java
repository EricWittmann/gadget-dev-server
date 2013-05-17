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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;

import javax.naming.InitialContext;
import javax.servlet.DispatcherType;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.shindig.auth.AuthenticationServletFilter;
import org.apache.shindig.common.servlet.GuiceServletContextListener;
import org.apache.shindig.common.servlet.HostFilter;
import org.apache.shindig.gadgets.servlet.ConcatProxyServlet;
import org.apache.shindig.gadgets.servlet.ETagFilter;
import org.apache.shindig.gadgets.servlet.GadgetRenderingServlet;
import org.apache.shindig.gadgets.servlet.HtmlAccelServlet;
import org.apache.shindig.gadgets.servlet.JsServlet;
import org.apache.shindig.gadgets.servlet.MakeRequestServlet;
import org.apache.shindig.gadgets.servlet.OAuth2CallbackServlet;
import org.apache.shindig.gadgets.servlet.OAuthCallbackServlet;
import org.apache.shindig.gadgets.servlet.ProxyServlet;
import org.apache.shindig.gadgets.servlet.RpcServlet;
import org.apache.shindig.gadgets.servlet.RpcSwfServlet;
import org.apache.shindig.protocol.DataServiceServlet;
import org.apache.shindig.protocol.JsonRpcServlet;
import org.apache.shindig.social.core.oauth2.OAuth2Servlet;
import org.apache.shindig.social.sample.oauth.SampleOAuthServlet;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.h2.Driver;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.overlord.commons.dev.server.DevServer;
import org.overlord.commons.dev.server.DevServerEnvironment;
import org.overlord.commons.dev.server.discovery.JarModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEGAVStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromMavenGAVStrategy;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.gadgets.web.server.StoreController;

/**
 * Dev environment bootstrapper for rtgov/bootstrapper.
 * @author eric.wittmann@redhat.com
 */
public class GadgetJettyDevServer extends DevServer {

    /**
     * Main entry point.
     * @param args
     */
    public static void main(String [] args) throws Exception {
        System.setProperty("discovery-strategy.debug", "true");
        GadgetJettyDevServer devServer = new GadgetJettyDevServer(args);
        devServer.go();
    }

    /**
     * Constructor.
     * @param args
     */
    public GadgetJettyDevServer(String [] args) {
        super(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#preConfig()
     */
    @Override
    protected void preConfig() {
        // Add JNDI resources
        try {
            InitialContext ctx = new InitialContext();
            ctx.bind("java:jboss", new InitialContext());
            ctx.bind("java:jboss/GadgetServer", createInMemoryDatasource());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#createDevEnvironment()
     */
    @Override
    protected DevServerEnvironment createDevEnvironment() {
        return new GadgetDevServerEnvironment(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModules(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void addModules(DevServerEnvironment environment) {
        environment.addModule("gadget-server",
                new WebAppModuleFromIDEGAVStrategy("org.overlord.gadgets.server", "gadget-server", false),
                new WebAppModuleFromMavenGAVStrategy("org.overlord.gadgets.server", "gadget-server"));
        environment.addModule("gadgets",
                new WebAppModuleFromIDEGAVStrategy("org.overlord.gadgets.server", "gadgets", true),
                new WebAppModuleFromMavenGAVStrategy("org.overlord.gadgets.server", "gadgets"));
        environment.addModule("gadget-web",
                new WebAppModuleFromIDEDiscoveryStrategy(StoreController.class),
                new WebAppModuleFromMavenDiscoveryStrategy(StoreController.class));
        environment.addModule("overlord-commons-uiheader",
                new JarModuleFromIDEDiscoveryStrategy(OverlordHeaderDataJS.class, "src/main/resources/META-INF/resources"),
                new JarModuleFromMavenDiscoveryStrategy(OverlordHeaderDataJS.class, "/META-INF/resources"));
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModulesToJetty(org.overlord.commons.dev.server.DevServerEnvironment, org.eclipse.jetty.server.handler.ContextHandlerCollection)
     */
    @Override
    protected void addModulesToJetty(DevServerEnvironment environment, ContextHandlerCollection handlers)
            throws Exception {
        URL[] clURLs = new URL[] {
                new File(environment.getModuleDir("gadget-server"), "WEB-INF/classes").toURI().toURL()
        };
        // Set up the classloader.
        ClassLoader cl = new URLClassLoader(clURLs, GadgetJettyDevServer.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(cl);

        /* *********
         * gadgets
         * ********* */
        ServletContextHandler gadgets = new ServletContextHandler(ServletContextHandler.SESSIONS);
        gadgets.setContextPath("/gadgets");
        gadgets.setResourceBase(environment.getModuleDir("gadgets").getCanonicalPath());
        // File resources
        ServletHolder resources = new ServletHolder(new DefaultServlet());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        gadgets.addServlet(resources, "/");


        /* *********
         * gadget-server
         * ********* */
        System.setProperty("shindig.host", "");
        System.setProperty("shindig.port", "");
        System.setProperty("aKey", "/shindig/gadgets/proxy?container=default&url=");
        ServletContextHandler gadgetServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        gadgetServer.setInitParameter("guice-modules", GUICE_MODULES);
        gadgetServer.setContextPath("/gadget-server");
        gadgetServer.setWelcomeFiles(new String[] { "samplecontainer/samplecontainer.html" });
        gadgetServer.setResourceBase(environment.getModuleDir("gadget-server").getCanonicalPath());
        gadgetServer.addEventListener(new GuiceServletContextListener());
        // HostFilter
        gadgetServer.addFilter(HostFilter.class, "/gadgets/ifr", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/gadgets/js/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/gadgets/proxy/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/gadgets/concat", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/gadgets/makeRequest", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/rpc/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(HostFilter.class, "/rest/*", EnumSet.of(DispatcherType.REQUEST));
        // ShiroFilter
        FilterHolder shiroFilter = new FilterHolder(IniShiroFilter.class);
        shiroFilter.setInitParameter("config", SHIRO_CONFIG);
        gadgetServer.addFilter(shiroFilter, "/oauth/authorize", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(shiroFilter, "/oauth2/authorize", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(shiroFilter, "*.jsp", EnumSet.of(DispatcherType.REQUEST));
        // AuthFilter
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/gadgets/ifr", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/gadgets/js/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/gadgets/proxy/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/gadgets/concat", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/gadgets/makeRequest", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/rpc/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetServer.addFilter(AuthenticationServletFilter.class, "/rest/*", EnumSet.of(DispatcherType.REQUEST));
        // EtagFilter
        gadgetServer.addFilter(ETagFilter.class, "*", EnumSet.of(DispatcherType.REQUEST));
        // Servlets
        gadgetServer.addServlet(GadgetRenderingServlet.class, "/gadgets/ifr");
        gadgetServer.addServlet(HtmlAccelServlet.class, "/gadgets/accel");
        gadgetServer.addServlet(ProxyServlet.class, "/gadgets/proxy/*");
        gadgetServer.addServlet(MakeRequestServlet.class, "/gadgets/makeRequest");
        gadgetServer.addServlet(ConcatProxyServlet.class, "/gadgets/concat");
        gadgetServer.addServlet(OAuthCallbackServlet.class, "/gadgets/oauthcallback");
        gadgetServer.addServlet(OAuth2CallbackServlet.class, "/gadgets/oauth2callback");
        gadgetServer.addServlet(RpcServlet.class, "/gadgets/metadata");
        gadgetServer.addServlet(JsServlet.class, "/gadgets/js/*");
        ServletHolder servletHolder = new ServletHolder(DataServiceServlet.class);
        servletHolder.setInitParameter("handlers", "org.apache.shindig.handlers");
        gadgetServer.addServlet(servletHolder, "/rest/*");
        gadgetServer.addServlet(servletHolder, "/gadgets/api/rest/*");
        gadgetServer.addServlet(servletHolder, "/social/rest/*");
        servletHolder = new ServletHolder(JsonRpcServlet.class);
        servletHolder.setInitParameter("handlers", "org.apache.shindig.handlers");
        gadgetServer.addServlet(servletHolder, "/rpc/*");
        gadgetServer.addServlet(servletHolder, "/gadgets/api/rpc/*");
        gadgetServer.addServlet(servletHolder, "/social/rpc/*");
        gadgetServer.addServlet(SampleOAuthServlet.class, "/oauth/*");
        gadgetServer.addServlet(OAuth2Servlet.class, "/oauth2/*");
        gadgetServer.addServlet(RpcSwfServlet.class, "/xpc*");
        // Resources
        resources = new ServletHolder(new DefaultServlet());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        gadgetServer.addServlet(resources, "/");


        /* *********
         * gadget-web
         * ********* */
        ServletContextHandler gadgetWeb = new ServletContextHandler(ServletContextHandler.SESSIONS);
        gadgetWeb.setInitParameter("resteasy.guice.modules", RE_GUICE_MODULES);
        gadgetWeb.setInitParameter("resteasy.servlet.mapping.prefix", "/rs");
        gadgetWeb.setContextPath("/gadget-web");
        gadgetWeb.setWelcomeFiles(new String[] { "Application.html" });
        gadgetWeb.setResourceBase(environment.getModuleDir("gadget-web").getCanonicalPath());
        gadgetWeb.addEventListener(new GuiceResteasyBootstrapServletContextListener());
        gadgetWeb.addServlet(HttpServletDispatcher.class, "/rs/*");
        // Resources
        resources = new ServletHolder(new DefaultServlet());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        gadgetWeb.addServlet(resources, "/");


        // Add the web contexts to jetty
        handlers.addHandler(gadgets);
        handlers.addHandler(gadgetServer);
        handlers.addHandler(gadgetWeb);
    }

    /**
     * Creates an in-memory datasource.
     * @throws SQLException
     */
    private static Object createInMemoryDatasource() throws SQLException {
        System.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(Driver.class.getName());
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        Connection connection = ds.getConnection();
        connection.close();
        return ds;
    }

    private static final String GUICE_MODULES = "" +
            "            org.apache.shindig.common.PropertiesModule:\r\n" +
            "            org.apache.shindig.gadgets.DefaultGuiceModule:\r\n" +
            "            org.apache.shindig.social.core.config.SocialApiGuiceModule:\r\n" +
            "            org.apache.shindig.social.sample.SampleModule:\r\n" +
            "            org.apache.shindig.gadgets.oauth.OAuthModule:\r\n" +
            "            org.apache.shindig.gadgets.oauth2.OAuth2Module:\r\n" +
            "            org.apache.shindig.gadgets.oauth2.OAuth2MessageModule:\r\n" +
            "            org.apache.shindig.gadgets.oauth2.handler.OAuth2HandlerModule:\r\n" +
            "            org.apache.shindig.gadgets.oauth2.persistence.sample.OAuth2PersistenceModule:\r\n" +
            "            org.apache.shindig.common.cache.ehcache.EhCacheModule:\r\n" +
            "            org.apache.shindig.sample.shiro.ShiroGuiceModule:\r\n" +
            "            org.apache.shindig.sample.container.SampleContainerGuiceModule:\r\n" +
            "            org.apache.shindig.extras.ShindigExtrasGuiceModule:\r\n" +
            "            org.apache.shindig.gadgets.admin.GadgetAdminModule";

    private static final String SHIRO_CONFIG = "" +
            "                # The ShiroFilter configuration is very powerful and flexible, while still remaining succinct.\r\n" +
            "                # Please read the comprehensive example, with full comments and explanations, in the JavaDoc:\r\n" +
            "                #\r\n" +
            "                # http://www.jsecurity.org/api/org/jsecurity/web/servlet/JSecurityFilter.html\r\n" +
            "                [main]\r\n" +
            "                shindigSampleRealm = org.apache.shindig.sample.shiro.SampleShiroRealm\r\n" +
            "                securityManager.realm = $shindigSampleRealm\r\n" +
            "                authc.loginUrl = /login.jsp\r\n" +
            "\r\n" +
            "                [urls]\r\n" +
            "                # The /login.jsp is not restricted to authenticated users (otherwise no one could log in!), but\r\n" +
            "                # the 'authc' filter must still be specified for it so it can process that url's\r\n" +
            "                # login submissions. It is 'smart' enough to allow those requests through as specified by the\r\n" +
            "                # shiro.loginUrl above.\r\n" +
            "                /login.jsp = authc\r\n" +
            "\r\n" +
            "                /oauth/authorize/** = authc\r\n" +
            "                /oauth2/authorize/** = authc\r\n" +
            "";

    private static final String RE_GUICE_MODULES = "" +
            "    org.overlord.gadgets.web.server.GadgetServerModule,\r\n" +
            "    org.overlord.gadgets.server.CoreModule\r\n";


}
