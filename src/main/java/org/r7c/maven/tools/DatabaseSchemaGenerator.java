package org.r7c.maven.tools;


import jakarta.persistence.Entity;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Goal which generates ddl files for given parameters .
 */
@Mojo(name = "generate-ddl", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true)
public class DatabaseSchemaGenerator extends AbstractMojo {

    public static final String PARAM_OUTPUT_DIR = "outputDirectory";
    public static final String PARAM_INPUT_MAPPING_DIRS = "xmlMappingDirectories";
    public static final String PARAM_DIALECTS = "dialects";
    public static final String PARAM_PACKAGES = "packages";
    public static final String PARAM_SCRIPT_FORMAT = "scriptFormat";
    public static final String PARAM_SCRIPT_LINE_DELIMITER = "scriptLineDelimiter";
    public static final String PARAM_TABLE_AUDIT_SUFFIX = "auditTableSuffix";
    public static final String PARAM_FILENAME_SUFFIX_CREATE = "filenameSuffixCreate";
    public static final String PARAM_FILENAME_SUFFIX_DROP = "filenameSuffixDrop";

    public static final String PARAM_COMMENT_TEMPLATE = "commentTemplate";
    public static final String PARAM_SCHEMA_VERSION = "schemaVersion";
    public static final String PARAM_APPLICATION = "application";
    public static final String PARAM_GENERATED_DATE = "generatedOn";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSchemaGenerator.class);

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    /**
     * Location of the file.
     */
    @Parameter(property = PARAM_OUTPUT_DIR, required = true, defaultValue = "${project.build.directory}")
    File outputDirectory;
    @Parameter(property = PARAM_INPUT_MAPPING_DIRS, required = true, defaultValue = "${project.resources[0].directory}/hbm/")
    List<File> xmlMappingDirectories;
    @Parameter(property = PARAM_DIALECTS, required = true, defaultValue = "org.hibernate.dialect.DerbyDialect")
    List<String> dialects;

    @Parameter(property = PARAM_PACKAGES)
    List<String> packages;
    @Parameter(property = PARAM_SCRIPT_FORMAT, defaultValue = "true")
    Boolean scriptFormat;
    @Parameter(property = PARAM_SCRIPT_LINE_DELIMITER, defaultValue = ";")
    String scriptLineDelimiter;

    @Parameter(property = PARAM_TABLE_AUDIT_SUFFIX, defaultValue = "_aud")
    String auditTableSuffix;
    @Parameter(property = PARAM_FILENAME_SUFFIX_CREATE, required = true, defaultValue = ".ddl")
    String filenameSuffixCreate;
    @Parameter(property = PARAM_FILENAME_SUFFIX_DROP, required = true, defaultValue = "-drop.ddl")
    String filenameSuffixDrop;

    @Parameter(property = PARAM_COMMENT_TEMPLATE, defaultValue = "-- ------------------------------------\n" +
            "-- Script version: ${schemaVersion}\n" +
            "-- Application: ${application}\n" +
            "-- Date: ${generatedOn}\n\n")
    String commentTemplate;

    @Parameter(property = PARAM_SCHEMA_VERSION, defaultValue = "${project.version}")
    String schemaVersion;
    @Parameter(property = PARAM_APPLICATION, defaultValue = "${project.artifactId}")
    String application;
    @Parameter(property = PARAM_GENERATED_DATE, defaultValue = "${maven.build.timestamp}")
    String generatedOn;

    public void execute() throws MojoExecutionException {
        // add project resources to the class loaded
        addProjectsResourcesToClassLoader();

        validateParameters();

        // execute
        for (String dialect : dialects) {
            try {
                createDDLScript(StringUtils.trim(dialect));
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException("Can not found annotation classes!", e);
            } catch (IOException e) {
                throw new MojoExecutionException("Can not read mapping files!", e);
            }
        }
    }

    public void validateParameters() {
        File fOutputDir = outputDirectory;
        if (!fOutputDir.exists()) {
            fOutputDir.mkdirs();
        }

        if (dialects == null || dialects.isEmpty()) {
            throw new IllegalArgumentException("At least one hibernate dialect must be set!");
        }

        if ((packages == null || packages.isEmpty())
                && (xmlMappingDirectories == null || xmlMappingDirectories.isEmpty())) {
            throw new IllegalArgumentException("At least one package with entities or xml mapping directory must be set!");
        }

        if (StringUtils.isBlank(filenameSuffixCreate)) {
            throw new IllegalArgumentException("Create filename suffix must not be null!");
        }

        if (StringUtils.isBlank(filenameSuffixDrop)) {
            throw new IllegalArgumentException("Drop filename suffix must not be null!");
        }
    }

    public void createDDLScript(String hibernateDialect) throws ClassNotFoundException, IOException {
        // create export file
        if (StringUtils.isNotBlank(auditTableSuffix)) {
            System.setProperty("org.hibernate.envers.audit_table_suffix", auditTableSuffix);
        }

        String filename = createFileName(hibernateDialect);
        String filenameDrop = createDropFileName(hibernateDialect);

        // metadata source
        MetadataSources metadata = new MetadataSources(new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, "jdbc:derby:memory:ddlgen;create=true") //  in-memory database because hibernate requires connection even if does not use it for script generation??.
                .applySetting(AvailableSettings.DIALECT, hibernateDialect)
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create")
                .build());


        // set mappings
        setMappingsToMetadata(metadata);
        // set packages
        setPackagesToMetadata(metadata);
        // build metadata implementor
        MetadataImplementor metadataImplementor = (MetadataImplementor) metadata.buildMetadata();

        // create schema exporter
        SchemaExport export = new SchemaExport();
        export.setFormat(scriptFormat);
        if (StringUtils.isNotBlank(scriptLineDelimiter)) {
            export.setDelimiter(scriptLineDelimiter);
        }

        File file = new File(outputDirectory, filename);

        if (file.exists()) { // delete if exists
            Files.delete(file.toPath());
            LOG.debug("File [{}] deleted - new will be generated!", file.getAbsolutePath());
        }

        File fileDrop = new File(outputDirectory, filenameDrop);
        if (fileDrop.exists()) { // delete if exists
            Files.delete(fileDrop.toPath());
            LOG.debug("File[{}] deleted - new will be generated!", fileDrop.getAbsolutePath());
        }
        LOG.debug("Export DDL create script with dialect: [{}] to file: [{}]", hibernateDialect, file.getAbsolutePath());

        String initialComment = getInitialComment();

        generateScript(export, SchemaExport.Action.CREATE, metadataImplementor, file, initialComment);

        generateScript(export, SchemaExport.Action.DROP, metadataImplementor, fileDrop, initialComment);
    }

    protected void setMappingsToMetadata(MetadataSources metadata) throws IOException {
        if (xmlMappingDirectories == null) {
            LOG.debug("No mappings folders defined!");
        }

        for (File xmlMappingDirectory : xmlMappingDirectories) {
            Path myDirectoryPath = xmlMappingDirectory.toPath();
            List<Path> subDirectories = Files.find(myDirectoryPath, Integer.MAX_VALUE,
                    (filePath, fileAttr) ->
                            fileAttr.isRegularFile() && (StringUtils.endsWithIgnoreCase(filePath.toFile().getName(), "hbm.xml")
                            || StringUtils.endsWithIgnoreCase(filePath.toFile().getName(), "orm.xml"))
                            && !filePath.equals(myDirectoryPath)).collect(Collectors.toList());

            subDirectories.forEach(path -> {
                LOG.debug("Add path [{}]", path);
                try (FileInputStream fis = new FileInputStream(path.toFile())) {
                    metadata.addInputStream(fis);
                } catch (IOException e) {
                    LOG.error("Error occurred while reading ddl xml binding", e);
                }
            });
        }
    }

    protected void setPackagesToMetadata(MetadataSources metadata) throws IOException, ClassNotFoundException {
        if (packages == null) {
            LOG.debug("No packages defined!");
        }
        // add annotated classes
        for (String pckName : packages) {
            // metadata.addPackage did not work... ??
            List<Class> clsList = getAllEntityClasses(pckName);
            for (Class clazz : clsList) {
                metadata.addAnnotatedClass(clazz);
            }
        }
    }

    protected void generateScript(SchemaExport export,
                                  SchemaExport.Action action,
                                  MetadataImplementor metadataImplementor,
                                  File outputFile,
                                  String initialComment) throws IOException {
        //chan change the output
        if (StringUtils.isNotBlank(initialComment)) {
            Files.write(outputFile.toPath(), initialComment.getBytes(), StandardOpenOption.CREATE);
        }
        export.setOutputFile(outputFile.getAbsolutePath());
        EnumSet<TargetType> enumSet = EnumSet.of(TargetType.SCRIPT);
        export.execute(enumSet, action, metadataImplementor);
    }

    protected String getInitialComment() {
        if (StringUtils.isNotBlank(commentTemplate)) {
            if (generatedOn == null) {
                generatedOn = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
            }
            return StringUtils.replaceEachRepeatedly(commentTemplate, new String[]
                            {"${schemaVersion}", "${generatedOn}", "${application}"},
                    new String[]{schemaVersion, generatedOn, application});
        }
        return null;
    }


    /**
     * Method creates filename based on dialect and version
     *
     * @param dialect
     * @return file name.
     */
    public String createFileName(String dialect) {
        String dbName = dialect.substring(dialect.lastIndexOf('.') + 1, dialect.lastIndexOf("Dialect")).toLowerCase();
        return dbName + filenameSuffixCreate;
    }

    public String createDropFileName(String dialect) {
        String dbName = dialect.substring(dialect.lastIndexOf('.') + 1, dialect.lastIndexOf("Dialect")).toLowerCase();
        return dbName + filenameSuffixDrop;
    }

    /**
     * Method adds project's compiled classes to the classloader.
     */
    public void addProjectsResourcesToClassLoader() {
        if (project == null) {
            LOG.warn("Can not add project resources! 'this.project' is null!");
            return;
        }
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];

            for (int i = 0; i < classpathElements.size(); ++i) {
                LOG.debug("Add project resources from: [{}]", classpathElements.get(i));
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            ClassLoader contextClassLoader = URLClassLoader.newInstance(
                    urls,
                    Thread.currentThread().getContextClassLoader());

            Thread.currentThread().setContextClassLoader(contextClassLoader);
        } catch (MalformedURLException | DependencyResolutionRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /***
     * Returns list of classes with entity annotations in package and subpackages.
     * @param packageNameValue
     * @return
     *
     * Method source
     * https://dzone.com/articles/get-all-classes-within-package
     */
    public List<Class> getAllEntityClasses(String packageNameValue) throws ClassNotFoundException, IOException {
        LOG.debug("Get all classes from the package: [{}]", packageNameValue);
        String packageName = StringUtils.trim(packageNameValue);
        if (StringUtils.isBlank(packageName)) {
            LOG.warn("Empty package names are skipped !");
            return Collections.emptyList();
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        if (dirs.isEmpty()) {
            LOG.warn("Package folder: [{}] does not exist! Package is skipped!", packageName);
            return Collections.emptyList();
        }
        ArrayList<Class> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        LOG.debug("In the package: [{}] found [{}] entity classes!", packageName, classes.size());
        return classes;
    }

    private List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<Class> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                Class clazz = classLoader.loadClass(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                if (clazz.isAnnotationPresent(Entity.class)) {
                    classes.add(clazz);
                }
            }
        }
        return classes;
    }
}


