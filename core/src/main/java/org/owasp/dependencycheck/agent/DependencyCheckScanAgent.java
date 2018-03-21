/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2014 Steve Springett. All Rights Reserved.
 */
package org.owasp.dependencycheck.agent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.data.update.exception.UpdateException;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Identifier;
import org.owasp.dependencycheck.dependency.Vulnerability;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.owasp.dependencycheck.exception.ReportException;
import org.owasp.dependencycheck.exception.ScanAgentException;
import org.owasp.dependencycheck.reporting.ReportGenerator;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a way to easily conduct a scan solely based on existing
 * evidence metadata rather than collecting evidence from the files themselves.
 * This class is based on the Ant task and Maven plugin with the exception that
 * it takes a list of dependencies that can be programmatically added from data
 * in a spreadsheet, database or some other datasource and conduct a scan based
 * on this pre-defined evidence.
 *
 * <h2>Example:</h2>
 * <pre>
 * List&lt;Dependency&gt; dependencies = new ArrayList&lt;Dependency&gt;();
 * Dependency dependency = new Dependency(new File(FileUtils.getBitBucket()));
 * dependency.addEvidence(EvidenceType.PRODUCT, "my-datasource", "name", "Jetty", Confidence.HIGH);
 * dependency.addEvidence(EvidenceType.VERSION, "my-datasource", "version", "5.1.10", Confidence.HIGH);
 * dependency.addEvidence(EvidenceType.VENDOR, "my-datasource", "vendor", "mortbay", Confidence.HIGH);
 * dependencies.add(dependency);
 *
 * DependencyCheckScanAgent scan = new DependencyCheckScanAgent();
 * scan.setDependencies(dependencies);
 * scan.setReportFormat(ReportGenerator.Format.ALL);
 * scan.setReportOutputDirectory(System.getProperty("user.home"));
 * scan.execute();
 * </pre>
 *
 * @author Steve Springett
 */
@SuppressWarnings("unused")
@NotThreadSafe
public class DependencyCheckScanAgent {

    //<editor-fold defaultstate="collapsed" desc="private fields">
    /**
     * System specific new line character.
     */
    private static final String NEW_LINE = System.getProperty("line.separator", "\n").intern();
    /**
     * Logger for use throughout the class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyCheckScanAgent.class);
    /**
     * The application name for the report.
     */
    private String applicationName = "Dependency-Check";
    /**
     * The pre-determined dependencies to scan
     */
    private List<Dependency> dependencies;
    /**
     * The location of the data directory that contains
     */
    private String dataDirectory = null;
    /**
     * Specifies the destination directory for the generated Dependency-Check
     * report.
     */
    private String reportOutputDirectory;
    /**
     * Specifies if the build should be failed if a CVSS score above a specified
     * level is identified. The default is 11 which means since the CVSS scores
     * are 0-10, by default the build will never fail and the CVSS score is set
     * to 11. The valid range for the fail build on CVSS is 0 to 11, where
     * anything above 10 will not cause the build to fail.
     */
    private float failBuildOnCVSS = 11;
    /**
     * Sets whether auto-updating of the NVD CVE/CPE data is enabled. It is not
     * recommended that this be turned to false. Default is true.
     */
    private boolean autoUpdate = true;
    /**
     * Sets whether the data directory should be updated without performing a
     * scan. Default is false.
     */
    private boolean updateOnly = false;
    /**
     * flag indicating whether or not to generate a report of findings.
     */
    private boolean generateReport = true;
    /**
     * The report format to be generated (HTML, XML, VULN, ALL). This
     * configuration option has no affect if using this within the Site plugin
     * unless the externalReport is set to true. Default is HTML.
     */
    private ReportGenerator.Format reportFormat = ReportGenerator.Format.HTML;
    /**
     * The Proxy Server.
     */
    private String proxyServer;
    /**
     * The Proxy Port.
     */
    private String proxyPort;
    /**
     * The Proxy username.
     */
    private String proxyUsername;
    /**
     * The Proxy password.
     */
    private String proxyPassword;
    /**
     * The Connection Timeout.
     */
    private String connectionTimeout;
    /**
     * The file path used for verbose logging.
     */
    private String logFile = null;
    /**
     * flag indicating whether or not to show a summary of findings.
     */
    private boolean showSummary = true;
    /**
     * The path to the suppression file.
     */
    private String suppressionFile;
    /**
     * The password to use when connecting to the database.
     */
    private String databasePassword;
    /**
     * The starting string that identifies CPEs that are qualified to be imported.
     */
    private String cpeStartsWithFilter;
    /**
     * Whether or not the Maven Central analyzer is enabled.
     */
    private boolean centralAnalyzerEnabled = true;
    /**
     * The URL of Maven Central.
     */
    private String centralUrl;
    /**
     * Whether or not the nexus analyzer is enabled.
     */
    private boolean nexusAnalyzerEnabled = true;
    /**
     * The URL of the Nexus server.
     */
    private String nexusUrl;
    /**
     * Whether or not the defined proxy should be used when connecting to Nexus.
     */
    private boolean nexusUsesProxy = true;
    /**
     * The database driver name; such as org.h2.Driver.
     */
    private String databaseDriverName;
    /**
     * The path to the database driver JAR file if it is not on the class path.
     */
    private String databaseDriverPath;
    /**
     * The database connection string.
     */
    private String connectionString;
    /**
     * The user name for connecting to the database.
     */
    private String databaseUser;
    /**
     * Additional ZIP File extensions to add analyze. This should be a
     * comma-separated list of file extensions to treat like ZIP files.
     */
    private String zipExtensions;
    /**
     * The url for the modified NVD CVE (1.2 schema).
     */
    private String cveUrl12Modified;
    /**
     * The url for the modified NVD CVE (2.0 schema).
     */
    private String cveUrl20Modified;
    /**
     * Base Data Mirror URL for CVE 1.2.
     */
    private String cveUrl12Base;
    /**
     * Data Mirror URL for CVE 2.0.
     */
    private String cveUrl20Base;
    /**
     * The path to dotnet for .NET assembly analysis on non-windows systems.
     */
    private String pathToDotnet;
    /**
     * The configured settings.
     */
    private Settings settings;
    /**
     * The path to optional dependency-check properties file. This will be used
     * to side-load additional user-defined properties.
     * {@link Settings#mergeProperties(String)}
     */
    private String propertiesFilePath;
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="getters/setters">

    /**
     * Get the value of applicationName.
     *
     * @return the value of applicationName
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Set the value of applicationName.
     *
     * @param applicationName new value of applicationName
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Returns a list of pre-determined dependencies.
     *
     * @return returns a list of dependencies
     */
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    /**
     * Sets the list of dependencies to scan.
     *
     * @param dependencies new value of dependencies
     */
    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    /**
     * Get the value of dataDirectory.
     *
     * @return the value of dataDirectory
     */
    public String getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Set the value of dataDirectory.
     *
     * @param dataDirectory new value of dataDirectory
     */
    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Get the value of reportOutputDirectory.
     *
     * @return the value of reportOutputDirectory
     */
    public String getReportOutputDirectory() {
        return reportOutputDirectory;
    }

    /**
     * Set the value of reportOutputDirectory.
     *
     * @param reportOutputDirectory new value of reportOutputDirectory
     */
    public void setReportOutputDirectory(String reportOutputDirectory) {
        this.reportOutputDirectory = reportOutputDirectory;
    }

    /**
     * Get the value of failBuildOnCVSS.
     *
     * @return the value of failBuildOnCVSS
     */
    public float getFailBuildOnCVSS() {
        return failBuildOnCVSS;
    }

    /**
     * Set the value of failBuildOnCVSS.
     *
     * @param failBuildOnCVSS new value of failBuildOnCVSS
     */
    public void setFailBuildOnCVSS(float failBuildOnCVSS) {
        this.failBuildOnCVSS = failBuildOnCVSS;
    }

    /**
     * Get the value of autoUpdate.
     *
     * @return the value of autoUpdate
     */
    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    /**
     * Set the value of autoUpdate.
     *
     * @param autoUpdate new value of autoUpdate
     */
    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    /**
     * Get the value of updateOnly.
     *
     * @return the value of updateOnly
     */
    public boolean isUpdateOnly() {
        return updateOnly;
    }

    /**
     * Set the value of updateOnly.
     *
     * @param updateOnly new value of updateOnly
     */
    public void setUpdateOnly(boolean updateOnly) {
        this.updateOnly = updateOnly;
    }

    /**
     * Get the value of generateReport.
     *
     * @return the value of generateReport
     */
    public boolean isGenerateReport() {
        return generateReport;
    }

    /**
     * Set the value of generateReport.
     *
     * @param generateReport new value of generateReport
     */
    public void setGenerateReport(boolean generateReport) {
        this.generateReport = generateReport;
    }

    /**
     * Get the value of reportFormat.
     *
     * @return the value of reportFormat
     */
    public ReportGenerator.Format getReportFormat() {
        return reportFormat;
    }

    /**
     * Set the value of reportFormat.
     *
     * @param reportFormat new value of reportFormat
     */
    public void setReportFormat(ReportGenerator.Format reportFormat) {
        this.reportFormat = reportFormat;
    }

    /**
     * Get the value of proxyServer.
     *
     * @return the value of proxyServer
     */
    public String getProxyServer() {
        return proxyServer;
    }

    /**
     * Set the value of proxyServer.
     *
     * @param proxyServer new value of proxyServer
     */
    public void setProxyServer(String proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * Get the value of proxyServer.
     *
     * @return the value of proxyServer
     * @deprecated use
     * {@link org.owasp.dependencycheck.agent.DependencyCheckScanAgent#getProxyServer()}
     * instead
     */
    @Deprecated
    public String getProxyUrl() {
        return proxyServer;
    }

    /**
     * Set the value of proxyServer.
     *
     * @param proxyUrl new value of proxyServer
     * @deprecated use {@link org.owasp.dependencycheck.agent.DependencyCheckScanAgent#setProxyServer(java.lang.String)
     * } instead
     */
    @Deprecated
    public void setProxyUrl(String proxyUrl) {
        this.proxyServer = proxyUrl;
    }

    /**
     * Get the value of proxyPort.
     *
     * @return the value of proxyPort
     */
    public String getProxyPort() {
        return proxyPort;
    }

    /**
     * Set the value of proxyPort.
     *
     * @param proxyPort new value of proxyPort
     */
    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Get the value of proxyUsername.
     *
     * @return the value of proxyUsername
     */
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Set the value of proxyUsername.
     *
     * @param proxyUsername new value of proxyUsername
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * Get the value of proxyPassword.
     *
     * @return the value of proxyPassword
     */
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Set the value of proxyPassword.
     *
     * @param proxyPassword new value of proxyPassword
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    /**
     * Get the value of connectionTimeout.
     *
     * @return the value of connectionTimeout
     */
    public String getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Set the value of connectionTimeout.
     *
     * @param connectionTimeout new value of connectionTimeout
     */
    public void setConnectionTimeout(String connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Get the value of logFile.
     *
     * @return the value of logFile
     */
    public String getLogFile() {
        return logFile;
    }

    /**
     * Set the value of logFile.
     *
     * @param logFile new value of logFile
     */
    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    /**
     * Get the value of suppressionFile.
     *
     * @return the value of suppressionFile
     */
    public String getSuppressionFile() {
        return suppressionFile;
    }

    /**
     * Set the value of suppressionFile.
     *
     * @param suppressionFile new value of suppressionFile
     */
    public void setSuppressionFile(String suppressionFile) {
        this.suppressionFile = suppressionFile;
    }

    /**
     * Get the value of showSummary.
     *
     * @return the value of showSummary
     */
    public boolean isShowSummary() {
        return showSummary;
    }

    /**
     * Set the value of showSummary.
     *
     * @param showSummary new value of showSummary
     */
    public void setShowSummary(boolean showSummary) {
        this.showSummary = showSummary;
    }

    /**
     * Sets starting string that identifies CPEs that are qualified to be imported.
     * @param cpeStartsWithFilter filters CPEs based on this starting string (i.e. cpe:/a: )
     */
    public void setCpeStartsWithFilter(String cpeStartsWithFilter) {
        this.cpeStartsWithFilter = cpeStartsWithFilter;
    }

    /**
     * Returns the starting string that identifies CPEs that are qualified to be imported.
     * @return the CPE starting filter (i.e. cpe:/a: )
     */
    public String getCpeStartsWithFilter() {
        return cpeStartsWithFilter;
    }

    /**
     * Get the value of centralAnalyzerEnabled.
     *
     * @return the value of centralAnalyzerEnabled
     */
    public boolean isCentralAnalyzerEnabled() {
        return centralAnalyzerEnabled;
    }

    /**
     * Set the value of centralAnalyzerEnabled.
     *
     * @param centralAnalyzerEnabled new value of centralAnalyzerEnabled
     */
    public void setCentralAnalyzerEnabled(boolean centralAnalyzerEnabled) {
        this.centralAnalyzerEnabled = centralAnalyzerEnabled;
    }

    /**
     * Get the value of centralUrl.
     *
     * @return the value of centralUrl
     */
    public String getCentralUrl() {
        return centralUrl;
    }

    /**
     * Set the value of centralUrl.
     *
     * @param centralUrl new value of centralUrl
     */
    public void setCentralUrl(String centralUrl) {
        this.centralUrl = centralUrl;
    }

    /**
     * Get the value of nexusAnalyzerEnabled.
     *
     * @return the value of nexusAnalyzerEnabled
     */
    public boolean isNexusAnalyzerEnabled() {
        return nexusAnalyzerEnabled;
    }

    /**
     * Set the value of nexusAnalyzerEnabled.
     *
     * @param nexusAnalyzerEnabled new value of nexusAnalyzerEnabled
     */
    public void setNexusAnalyzerEnabled(boolean nexusAnalyzerEnabled) {
        this.nexusAnalyzerEnabled = nexusAnalyzerEnabled;
    }

    /**
     * Get the value of nexusUrl.
     *
     * @return the value of nexusUrl
     */
    public String getNexusUrl() {
        return nexusUrl;
    }

    /**
     * Set the value of nexusUrl.
     *
     * @param nexusUrl new value of nexusUrl
     */
    public void setNexusUrl(String nexusUrl) {
        this.nexusUrl = nexusUrl;
    }

    /**
     * Get the value of nexusUsesProxy.
     *
     * @return the value of nexusUsesProxy
     */
    public boolean isNexusUsesProxy() {
        return nexusUsesProxy;
    }

    /**
     * Set the value of nexusUsesProxy.
     *
     * @param nexusUsesProxy new value of nexusUsesProxy
     */
    public void setNexusUsesProxy(boolean nexusUsesProxy) {
        this.nexusUsesProxy = nexusUsesProxy;
    }

    /**
     * Get the value of databaseDriverName.
     *
     * @return the value of databaseDriverName
     */
    public String getDatabaseDriverName() {
        return databaseDriverName;
    }

    /**
     * Set the value of databaseDriverName.
     *
     * @param databaseDriverName new value of databaseDriverName
     */
    public void setDatabaseDriverName(String databaseDriverName) {
        this.databaseDriverName = databaseDriverName;
    }

    /**
     * Get the value of databaseDriverPath.
     *
     * @return the value of databaseDriverPath
     */
    public String getDatabaseDriverPath() {
        return databaseDriverPath;
    }

    /**
     * Set the value of databaseDriverPath.
     *
     * @param databaseDriverPath new value of databaseDriverPath
     */
    public void setDatabaseDriverPath(String databaseDriverPath) {
        this.databaseDriverPath = databaseDriverPath;
    }

    /**
     * Get the value of connectionString.
     *
     * @return the value of connectionString
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Set the value of connectionString.
     *
     * @param connectionString new value of connectionString
     */
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Get the value of databaseUser.
     *
     * @return the value of databaseUser
     */
    public String getDatabaseUser() {
        return databaseUser;
    }

    /**
     * Set the value of databaseUser.
     *
     * @param databaseUser new value of databaseUser
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    /**
     * Get the value of databasePassword.
     *
     * @return the value of databasePassword
     */
    public String getDatabasePassword() {
        return databasePassword;
    }

    /**
     * Set the value of databasePassword.
     *
     * @param databasePassword new value of databasePassword
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    /**
     * Get the value of zipExtensions.
     *
     * @return the value of zipExtensions
     */
    public String getZipExtensions() {
        return zipExtensions;
    }

    /**
     * Set the value of zipExtensions.
     *
     * @param zipExtensions new value of zipExtensions
     */
    public void setZipExtensions(String zipExtensions) {
        this.zipExtensions = zipExtensions;
    }

    /**
     * Get the value of cveUrl12Modified.
     *
     * @return the value of cveUrl12Modified
     */
    public String getCveUrl12Modified() {
        return cveUrl12Modified;
    }

    /**
     * Set the value of cveUrl12Modified.
     *
     * @param cveUrl12Modified new value of cveUrl12Modified
     */
    public void setCveUrl12Modified(String cveUrl12Modified) {
        this.cveUrl12Modified = cveUrl12Modified;
    }

    /**
     * Get the value of cveUrl20Modified.
     *
     * @return the value of cveUrl20Modified
     */
    public String getCveUrl20Modified() {
        return cveUrl20Modified;
    }

    /**
     * Set the value of cveUrl20Modified.
     *
     * @param cveUrl20Modified new value of cveUrl20Modified
     */
    public void setCveUrl20Modified(String cveUrl20Modified) {
        this.cveUrl20Modified = cveUrl20Modified;
    }

    /**
     * Get the value of cveUrl12Base.
     *
     * @return the value of cveUrl12Base
     */
    public String getCveUrl12Base() {
        return cveUrl12Base;
    }

    /**
     * Set the value of cveUrl12Base.
     *
     * @param cveUrl12Base new value of cveUrl12Base
     */
    public void setCveUrl12Base(String cveUrl12Base) {
        this.cveUrl12Base = cveUrl12Base;
    }

    /**
     * Get the value of cveUrl20Base.
     *
     * @return the value of cveUrl20Base
     */
    public String getCveUrl20Base() {
        return cveUrl20Base;
    }

    /**
     * Set the value of cveUrl20Base.
     *
     * @param cveUrl20Base new value of cveUrl20Base
     */
    public void setCveUrl20Base(String cveUrl20Base) {
        this.cveUrl20Base = cveUrl20Base;
    }

    /**
     * Get the value of pathToDotnet.
     *
     * @return the value of pathToDotnet
     */
    public String getpathToDotnet() {
        return pathToDotnet;
    }

    /**
     * Set the value of pathToDotnet.
     *
     * @param pathToDotnet new value of pathToDotnet
     */
    public void setpathToDotnet(String pathToDotnet) {
        this.pathToDotnet = pathToDotnet;
    }

    /**
     * Get the value of propertiesFilePath.
     *
     * @return the value of propertiesFilePath
     */
    public String getPropertiesFilePath() {
        return propertiesFilePath;
    }

    /**
     * Set the value of propertiesFilePath.
     *
     * @param propertiesFilePath new value of propertiesFilePath
     */
    public void setPropertiesFilePath(String propertiesFilePath) {
        this.propertiesFilePath = propertiesFilePath;
    }
    //</editor-fold>

    /**
     * Executes the Dependency-Check on the dependent libraries. <b>Note</b>, the engine
     * object returned from this method must be closed by calling `close()`
     *
     * @return the Engine used to scan the dependencies.
     * @throws ExceptionCollection a collection of one or more exceptions that
     * occurred during analysis.
     */
    private Engine executeDependencyCheck() throws ExceptionCollection {
        populateSettings();
        final Engine engine;
        try {
            engine = new Engine(settings);
        } catch (DatabaseException ex) {
            throw new ExceptionCollection(ex, true);
        }
        if (this.updateOnly) {
            try {
                engine.doUpdates();
            } catch (UpdateException ex) {
                throw new ExceptionCollection("Unable to perform update", ex);
            } finally {
                engine.close();
            }
        } else {
            engine.setDependencies(this.dependencies);
            engine.analyzeDependencies();
        }
        return engine;
    }

    /**
     * Generates the reports for a given dependency-check engine.
     *
     * @param engine a dependency-check engine
     * @param outDirectory the directory to write the reports to
     * @throws ScanAgentException thrown if there is an error generating the
     * report
     */
    private void generateExternalReports(Engine engine, File outDirectory) throws ScanAgentException {
        try {
            engine.writeReports(applicationName, outDirectory, this.reportFormat.name());
        } catch (ReportException ex) {
            LOGGER.debug("Unexpected exception occurred during analysis; please see the verbose error log for more details.", ex);
            throw new ScanAgentException("Error generating the report", ex);
        }
    }

    /**
     * Takes the properties supplied and updates the dependency-check settings.
     * Additionally, this sets the system properties required to change the
     * proxy server, port, and connection timeout.
     */
    private void populateSettings() {
        settings = new Settings();
        if (dataDirectory != null) {
            settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDirectory);
        } else {
            final File jarPath = new File(DependencyCheckScanAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            final File base = jarPath.getParentFile();
            final String sub = settings.getString(Settings.KEYS.DATA_DIRECTORY);
            final File dataDir = new File(base, sub);
            settings.setString(Settings.KEYS.DATA_DIRECTORY, dataDir.getAbsolutePath());
        }
        if (propertiesFilePath != null) {
            try {
                settings.mergeProperties(propertiesFilePath);
                LOGGER.info("Successfully loaded user-defined properties");
            } catch (IOException e) {
                LOGGER.error("Unable to merge user-defined properties", e);
                LOGGER.error("Continuing execution");
            }
        }

        settings.setBoolean(Settings.KEYS.AUTO_UPDATE, autoUpdate);
        settings.setStringIfNotEmpty(Settings.KEYS.PROXY_SERVER, proxyServer);
        settings.setStringIfNotEmpty(Settings.KEYS.PROXY_PORT, proxyPort);
        settings.setStringIfNotEmpty(Settings.KEYS.PROXY_USERNAME, proxyUsername);
        settings.setStringIfNotEmpty(Settings.KEYS.PROXY_PASSWORD, proxyPassword);
        settings.setStringIfNotEmpty(Settings.KEYS.CONNECTION_TIMEOUT, connectionTimeout);
        settings.setStringIfNotEmpty(Settings.KEYS.SUPPRESSION_FILE, suppressionFile);
        settings.setStringIfNotEmpty(Settings.KEYS.CVE_CPE_STARTS_WITH_FILTER, cpeStartsWithFilter);
        settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, centralAnalyzerEnabled);
        settings.setStringIfNotEmpty(Settings.KEYS.ANALYZER_CENTRAL_URL, centralUrl);
        settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, nexusAnalyzerEnabled);
        settings.setStringIfNotEmpty(Settings.KEYS.ANALYZER_NEXUS_URL, nexusUrl);
        settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_USES_PROXY, nexusUsesProxy);
        settings.setStringIfNotEmpty(Settings.KEYS.DB_DRIVER_NAME, databaseDriverName);
        settings.setStringIfNotEmpty(Settings.KEYS.DB_DRIVER_PATH, databaseDriverPath);
        settings.setStringIfNotEmpty(Settings.KEYS.DB_CONNECTION_STRING, connectionString);
        settings.setStringIfNotEmpty(Settings.KEYS.DB_USER, databaseUser);
        settings.setStringIfNotEmpty(Settings.KEYS.DB_PASSWORD, databasePassword);
        settings.setStringIfNotEmpty(Settings.KEYS.ADDITIONAL_ZIP_EXTENSIONS, zipExtensions);
        settings.setStringIfNotEmpty(Settings.KEYS.CVE_MODIFIED_12_URL, cveUrl12Modified);
        settings.setStringIfNotEmpty(Settings.KEYS.CVE_MODIFIED_20_URL, cveUrl20Modified);
        settings.setStringIfNotEmpty(Settings.KEYS.CVE_SCHEMA_1_2, cveUrl12Base);
        settings.setStringIfNotEmpty(Settings.KEYS.CVE_SCHEMA_2_0, cveUrl20Base);
        settings.setStringIfNotEmpty(Settings.KEYS.ANALYZER_ASSEMBLY_DOTNET_PATH, pathToDotnet);
    }

    /**
     * Executes the dependency-check and generates the report.
     *
     * @return a reference to the engine used to perform the scan.
     * @throws org.owasp.dependencycheck.exception.ScanAgentException thrown if
     * there is an exception executing the scan.
     */
    public Engine execute() throws ScanAgentException {
        Engine engine = null;
        try {
            engine = executeDependencyCheck();
            if (!this.updateOnly) {
                if (this.generateReport) {
                    generateExternalReports(engine, new File(this.reportOutputDirectory));
                }
                if (this.showSummary) {
                    showSummary(engine.getDependencies());
                }
                if (this.failBuildOnCVSS <= 10) {
                    checkForFailure(engine.getDependencies());
                }
            }
        } catch (ExceptionCollection ex) {
            if (ex.isFatal()) {
                LOGGER.error("A fatal exception occurred during analysis; analysis has stopped. Please see the debug log for more details.");
                LOGGER.debug("", ex);
            }
            throw new ScanAgentException("One or more exceptions occurred during analysis; please see the debug log for more details.", ex);
        } finally {
            settings.cleanup(true);
            if (engine != null) {
                engine.close();
            }
        }
        return engine;
    }

    /**
     * Checks to see if a vulnerability has been identified with a CVSS score
     * that is above the threshold set in the configuration.
     *
     * @param dependencies the list of dependency objects
     * @throws org.owasp.dependencycheck.exception.ScanAgentException thrown if
     * there is an exception executing the scan.
     */
    private void checkForFailure(Dependency[] dependencies) throws ScanAgentException {
        final StringBuilder ids = new StringBuilder();
        for (Dependency d : dependencies) {
            boolean addName = true;
            for (Vulnerability v : d.getVulnerabilities()) {
                if (v.getCvssScore() >= failBuildOnCVSS) {
                    if (addName) {
                        addName = false;
                        ids.append(NEW_LINE).append(d.getFileName()).append(": ");
                        ids.append(v.getName());
                    } else {
                        ids.append(", ").append(v.getName());
                    }
                }
            }
        }
        if (ids.length() > 0) {
            final String msg;
            if (showSummary) {
                msg = String.format("%n%nDependency-Check Failure:%n"
                        + "One or more dependencies were identified with vulnerabilities that have a CVSS score greater than or equal to '%.1f': %s%n"
                        + "See the dependency-check report for more details.%n%n", failBuildOnCVSS, ids.toString());
            } else {
                msg = String.format("%n%nDependency-Check Failure:%n"
                        + "One or more dependencies were identified with vulnerabilities.%n%n"
                        + "See the dependency-check report for more details.%n%n");
            }
            throw new ScanAgentException(msg);
        }
    }

    /**
     * Generates a warning message listing a summary of dependencies and their
     * associated CPE and CVE entries.
     *
     * @param dependencies a list of dependency objects
     */
    private void showSummary(Dependency[] dependencies) {
        final StringBuilder summary = new StringBuilder();
        for (Dependency d : dependencies) {
            boolean firstEntry = true;
            final StringBuilder ids = new StringBuilder();
            for (Vulnerability v : d.getVulnerabilities(true)) {
                if (firstEntry) {
                    firstEntry = false;
                } else {
                    ids.append(", ");
                }
                ids.append(v.getName());
            }
            if (ids.length() > 0) {
                summary.append(d.getFileName()).append(" (");
                firstEntry = true;
                for (Identifier id : d.getIdentifiers()) {
                    if (firstEntry) {
                        firstEntry = false;
                    } else {
                        summary.append(", ");
                    }
                    summary.append(id.getValue());
                }
                summary.append(") : ").append(ids).append(NEW_LINE);
            }
        }
        if (summary.length() > 0) {
            LOGGER.warn("\n\nOne or more dependencies were identified with known vulnerabilities:\n\n{}\n\n"
                    + "See the dependency-check report for more details.\n\n",
                    summary.toString());
        }
    }
}
