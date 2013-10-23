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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;

import javax.naming.InitialContext;
import javax.servlet.DispatcherType;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
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
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.h2.Driver;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.overlord.commons.dev.server.DevServer;
import org.overlord.commons.dev.server.DevServerEnvironment;
import org.overlord.commons.dev.server.MultiDefaultServlet;
import org.overlord.commons.dev.server.discovery.JarModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEGAVStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromMavenGAVStrategy;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.gadgets.server.Bootstrap;
import org.overlord.gadgets.server.mock.OverlordRTGovMockServlet;
import org.overlord.gadgets.web.server.StoreController;
import org.overlord.gadgets.web.server.filters.JSONPFilter;
import org.overlord.gadgets.web.server.http.auth.AuthenticationConstants;
import org.overlord.gadgets.web.server.http.auth.BasicAuthenticationProvider;
import org.overlord.gadgets.web.server.listeners.ShindigResteasyBootstrapServletContextListener;
import org.overlord.gadgets.web.server.servlets.RestProxyBasicAuthProvider;
import org.overlord.gadgets.web.server.servlets.RestProxyServlet;

/**
 * Dev environment bootstrapper for rtgov/bootstrapper.
 * @author eric.wittmann@redhat.com
 */
public class GadgetDevServer extends DevServer {

    private DataSource ds = null;

    /**
     * Main entry point.
     * @param args
     */
    public static void main(String [] args) throws Exception {
        GadgetDevServer devServer = new GadgetDevServer(args);
        devServer.go();
    }

    /**
     * Constructor.
     * @param args
     */
    public GadgetDevServer(String [] args) {
        super(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#preConfig()
     */
    @Override
    protected void preConfig() {
        System.setProperty(Bootstrap.HIBERNATE_HBM2DDL_AUTO, "create-drop");
        // Configure shindig data services authentication
        System.setProperty(AuthenticationConstants.CONFIG_AUTHENTICATION_PROVIDER, BasicAuthenticationProvider.class.getName());
        System.setProperty(AuthenticationConstants.CONFIG_BASIC_AUTH_USER, "rest-client");
        System.setProperty(AuthenticationConstants.CONFIG_BASIC_AUTH_PASS, "rest-client");
        System.setProperty(AuthenticationConstants.CONFIG_AUTHENTICATION_ENDPOINTS, "/overlord-rtgov/");
        // Configure REST proxy authentication
        System.setProperty("gadget-server.rest-proxy.service-overview.authentication-provider", RestProxyBasicAuthProvider.class.getName());
        System.setProperty("gadget-server.rest-proxy.service-overview.authentication.basic.username", "rest-client");
        System.setProperty("gadget-server.rest-proxy.service-overview.authentication.basic.password", "rest-client");

        // Add JNDI resources
        try {
            InitialContext ctx = new InitialContext();
            ctx.bind("java:jboss", new InitialContext());
            ctx.bind("java:jboss/datasources", new InitialContext());
            ds = createInMemoryDatasource();
            ctx.bind("java:jboss/datasources/GadgetServer", ds);
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
                new WebAppModuleFromIDEGAVStrategy("org.overlord.rtgov", "gadgets", true),
                new WebAppModuleFromMavenGAVStrategy("org.overlord.rtgov", "gadgets"));
        environment.addModule("gadget-web",
                new WebAppModuleFromIDEGAVStrategy("org.overlord.gadgets.server", "gadget-web", true),
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
        ClassLoader cl = new URLClassLoader(clURLs, GadgetDevServer.class.getClassLoader());
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
         * gadget-web
         * ********* */
        System.setProperty("shindig.host", "");
        System.setProperty("shindig.port", "");
        System.setProperty("aKey", "/shindig/gadgets/proxy?container=default&url=");
        ServletContextHandler gadgetWeb = new ServletContextHandler(ServletContextHandler.SESSIONS);
        gadgetWeb.setInitParameter("guice-modules", GUICE_MODULES);
        gadgetWeb.setInitParameter("resteasy.servlet.mapping.prefix", "/rs");
        gadgetWeb.setContextPath("/gadget-web");
        gadgetWeb.setWelcomeFiles(new String[] { "Application.html" });
//        gadgetServer.setResourceBase(environment.getModuleDir("gadget-server").getCanonicalPath());
        gadgetWeb.addEventListener(new GuiceServletContextListener());
        gadgetWeb.addEventListener(new ShindigResteasyBootstrapServletContextListener());
        // JSONP filter
        gadgetWeb.addFilter(JSONPFilter.class, "/rs/*", EnumSet.of(DispatcherType.REQUEST));
        // HostFilter
        gadgetWeb.addFilter(HostFilter.class, "/gadgets/ifr", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/gadgets/js/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/gadgets/proxy/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/gadgets/concat", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/gadgets/makeRequest", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/rpc/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(HostFilter.class, "/rest/*", EnumSet.of(DispatcherType.REQUEST));
        // ShiroFilter
        FilterHolder shiroFilter = new FilterHolder(IniShiroFilter.class);
        shiroFilter.setInitParameter("config", SHIRO_CONFIG);
        gadgetWeb.addFilter(shiroFilter, "/oauth/authorize", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(shiroFilter, "/oauth2/authorize", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(shiroFilter, "*.jsp", EnumSet.of(DispatcherType.REQUEST));
        // AuthFilter
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/gadgets/ifr", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/gadgets/js/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/gadgets/proxy/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/gadgets/concat", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/gadgets/makeRequest", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/rpc/*", EnumSet.of(DispatcherType.REQUEST));
        gadgetWeb.addFilter(AuthenticationServletFilter.class, "/rest/*", EnumSet.of(DispatcherType.REQUEST));
        // EtagFilter
        gadgetWeb.addFilter(ETagFilter.class, "*", EnumSet.of(DispatcherType.REQUEST));
        // Servlets
        gadgetWeb.addServlet(GadgetRenderingServlet.class, "/gadgets/ifr");
        gadgetWeb.addServlet(HtmlAccelServlet.class, "/gadgets/accel");
        gadgetWeb.addServlet(ProxyServlet.class, "/gadgets/proxy/*");
        gadgetWeb.addServlet(MakeRequestServlet.class, "/gadgets/makeRequest");
        gadgetWeb.addServlet(ConcatProxyServlet.class, "/gadgets/concat");
        gadgetWeb.addServlet(OAuthCallbackServlet.class, "/gadgets/oauthcallback");
        gadgetWeb.addServlet(OAuth2CallbackServlet.class, "/gadgets/oauth2callback");
        gadgetWeb.addServlet(RpcServlet.class, "/gadgets/metadata");
        gadgetWeb.addServlet(JsServlet.class, "/gadgets/js/*");
        ServletHolder servletHolder = new ServletHolder(DataServiceServlet.class);
        servletHolder.setInitParameter("handlers", "org.apache.shindig.handlers");
        gadgetWeb.addServlet(servletHolder, "/rest/*");
        gadgetWeb.addServlet(servletHolder, "/gadgets/api/rest/*");
        gadgetWeb.addServlet(servletHolder, "/social/rest/*");
        servletHolder = new ServletHolder(JsonRpcServlet.class);
        servletHolder.setInitParameter("handlers", "org.apache.shindig.handlers");
        gadgetWeb.addServlet(servletHolder, "/rpc/*");
        gadgetWeb.addServlet(servletHolder, "/gadgets/api/rpc/*");
        gadgetWeb.addServlet(servletHolder, "/social/rpc/*");
        gadgetWeb.addServlet(SampleOAuthServlet.class, "/oauth/*");
        gadgetWeb.addServlet(OAuth2Servlet.class, "/oauth2/*");
        gadgetWeb.addServlet(RpcSwfServlet.class, "/xpc*");
        gadgetWeb.addServlet(HttpServletDispatcher.class, "/rs/*");
        // Service Overview REST service proxy servlet
        ServletHolder soProxyServlet = gadgetWeb.addServlet(RestProxyServlet.class, "/service/dependency/overview");
        soProxyServlet.setInitParameter("proxy-name", "service-overview");
        soProxyServlet.setInitParameter("proxy-url", "SCHEME://HOST:PORT/overlord-rtgov/service/dependency/overview");
        // Overlord Header JS servlet
        ServletHolder overlordHeaderJS = new ServletHolder(OverlordHeaderDataJS.class);
        overlordHeaderJS.setInitParameter("app-id", "gadget-server");
        gadgetWeb.addServlet(overlordHeaderJS, "/js/overlord-header-data.js");
        // File resources
        resources = new ServletHolder(new MultiDefaultServlet());
        resources.setInitParameter("resourceBase", "/");
        resources.setInitParameter("resourceBases", environment.getModuleDir("gadget-web").getCanonicalPath()
                + "|" + environment.getModuleDir("gadget-server").getCanonicalPath()
                + "|" + environment.getModuleDir("overlord-commons-uiheader").getCanonicalPath());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        gadgetWeb.addServlet(resources, "/");
        gadgetWeb.setSecurityHandler(createSecurityHandler());

        /* *********
         * rtgov mock
         * ********* */
        ServletContextHandler rtgov = new ServletContextHandler(ServletContextHandler.SESSIONS);
        rtgov.setContextPath("/overlord-rtgov");
        rtgov.setResourceBase(environment.getModuleDir("gadgets").getCanonicalPath());
        rtgov.addServlet(OverlordRTGovMockServlet.class, "/");
        rtgov.setSecurityHandler(createRtGovSecurityHandler());


        // Add the web contexts to jetty
        handlers.addHandler(gadgets);
        handlers.addHandler(gadgetWeb);
        handlers.addHandler(rtgov);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#postStart(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void postStart(DevServerEnvironment environment) throws Exception {
        bootstrapGadgetDB();
        seedDB(ds);
    }

    /**
     * @throws MalformedURLException
     * @throws IOException
     */
    private void bootstrapGadgetDB() throws MalformedURLException, IOException {
        String urlStr = String.format("http://localhost:%1$d/gadget-web/rs/stores/all/0/1", serverPort());

        HttpClient client = new HttpClient();
        client.getState().setCredentials(
            new AuthScope("localhost", serverPort(), "overlordrealm"),
            new UsernamePasswordCredentials("admin", "admin")
        );

        GetMethod get = new GetMethod(urlStr);
        get.setDoAuthentication( true );
        try {
            int status = client.executeMethod( get );
            if (status != 200) {
                throw new RuntimeException("Error bootstrapping DB: " + status);
            }
        } finally {
            get.releaseConnection();
        }
    }

    /**
     * Creates a basic auth security handler.
     */
    private SecurityHandler createSecurityHandler() {
        HashLoginService l = new HashLoginService();
        for (String user : USERS) {
            l.putUser(user, Credential.getCredential(user), new String[] {"user"});
        }
        l.setName("overlordrealm");

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("overlordrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

    /**
     * Creates a basic auth security handler for the rtgov mock rest endpoints.
     */
    private SecurityHandler createRtGovSecurityHandler() {
        HashLoginService l = new HashLoginService();
        for (String user : RTGOV_USERS) {
            l.putUser(user, Credential.getCredential(user), new String[] {"client"});
        }
        l.setName("rtgovrealm");

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"client"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("rtgovrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

    /**
     * Creates an in-memory datasource.
     * @throws SQLException
     */
    private static DataSource createInMemoryDatasource() throws SQLException {
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

    /**
     * @param ds
     * @throws SQLException
     * @throws IOException
     */
    private void seedDB(DataSource ds) throws SQLException, IOException {
        Connection connection = ds.getConnection();

        try {
            String sql = DB_SEED_DATA;
            BufferedReader reader = new BufferedReader(new StringReader(sql));
            String line = null;
            while ( (line = reader.readLine()) != null) {
                if (line.trim().length() > 0) {
                    System.out.println(" DB> " + line);
                    connection.createStatement().execute(line);
                }
            }

            connection.commit();
        } finally {
            connection.close();
        }
    }

    private static final String GUICE_MODULES = "" +
            "    org.overlord.gadgets.web.server.GadgetServerModule:\r\n" +
            "    org.overlord.gadgets.server.CoreModule:\r\n" +
            "    org.overlord.gadgets.web.server.http.auth.AuthenticationModule:\r\n" +
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

    private static final String DB_SEED_DATA =
            "INSERT INTO GS_GROUP(`GROUP_ID`,`GROUP_NAME`, `GROUP_DESC`) VALUES(1, 'system', 'reserved system group');\r\n" +
            "INSERT INTO GS_GADGET(`GADGET_TITLE`,`GADGET_AUTHOR`,`GADGET_AUTHOR_EMAIL`,`GADGET_DESCRIPTION`,`GADGET_THUMBNAIL_URL`,`GADGET_URL`) VALUES('Response Time','Jeff Yu','jeffyu@overlord.com','Response Time Gadget','http://localhost:8080/gadgets/rt-gadget/thumbnail.png','http://localhost:8080/gadgets/rt-gadget/gadget.xml');\r\n" +
            "INSERT INTO GS_GADGET(`GADGET_TITLE`,`GADGET_AUTHOR`,`GADGET_AUTHOR_EMAIL`,`GADGET_DESCRIPTION`,`GADGET_THUMBNAIL_URL`,`GADGET_URL`) VALUES('Date & Time','Google','admin@google.com','Add a clock to your page. Click edit to change it to the color of your choice','http://gadgets.adwebmaster.net/images/gadgets/datetimemulti/thumbnail_en.jpg','http://www.gstatic.com/ig/modules/datetime_v3/datetime_v3.xml');\r\n" +
            "INSERT INTO GS_GADGET(`GADGET_TITLE`,`GADGET_AUTHOR`,`GADGET_AUTHOR_EMAIL`,`GADGET_DESCRIPTION`,`GADGET_THUMBNAIL_URL`,`GADGET_URL`) VALUES('Currency Converter','Google','info@tofollow.com','currency converter widget','http://www.gstatic.com/ig/modules/currency_converter/currency_converter_content/en_us-thm.cache.png','http://www.gstatic.com/ig/modules/currency_converter/currency_converter_v2.xml');\r\n" +
            "INSERT INTO GS_GADGET(`GADGET_TITLE`,`GADGET_AUTHOR`,`GADGET_AUTHOR_EMAIL`,`GADGET_DESCRIPTION`,`GADGET_THUMBNAIL_URL`,`GADGET_URL`) VALUES('Economic Data - ALFRED Graph','Research Department','webmaster@research.stlouisfed.org','Vintage Economic Data from the Federal Reserve Bank of St. Louis','http://research.stlouisfed.org/gadgets/images/alfredgraphgadgetthumbnail.png','http://research.stlouisfed.org/gadgets/code/alfredgraph.xml');";

    private static final String [] USERS = { "admin", "eric", "gary", "jeff" };
    private static final String [] RTGOV_USERS = { "rest-client" };
}
