package org.r7c.maven.tools;


import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.DefaultBuildPluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static org.r7c.maven.tools.DatabaseSchemaGenerator.*;

public class DatabaseSchemaGeneratorTest extends AbstractMojoTestCase {

    private final File testPomFile = Paths.get("src", "test", "resources", "pom.xml").toFile();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testGenerateDDLAssertReadingParameters()
            throws Exception {

        assertTrue(testPomFile.exists());
        DatabaseSchemaGenerator testPlugin = (DatabaseSchemaGenerator) lookupMojo("generate-ddl", testPomFile);
        assertNotNull(testPlugin);
        File outputDirectory = (File) getVariableValueFromObject(testPlugin, PARAM_OUTPUT_DIR);
        assertNotNull(outputDirectory);

        assertNotNull(testPlugin);
        List<File> inputMappings = (List<File>) getVariableValueFromObject(testPlugin, PARAM_INPUT_MAPPING_DIRS);
        assertNotNull(inputMappings);
        assertEquals(2, inputMappings.size());

        List<String> dialects = (List<String>) getVariableValueFromObject(testPlugin, PARAM_DIALECTS);
        assertNotNull(dialects);
        assertFalse(dialects.isEmpty());

        List<String> packages = (List<String>) getVariableValueFromObject(testPlugin, PARAM_PACKAGES);
        assertNotNull(packages);
        assertEquals(1, packages.size());

        Boolean format = (Boolean) getVariableValueFromObject(testPlugin, PARAM_SCRIPT_FORMAT);
        assertTrue(format);

        String delimiter = (String) getVariableValueFromObject(testPlugin, PARAM_SCRIPT_LINE_DELIMITER);
        assertEquals(";", delimiter);

        String auditTableSuffix = (String) getVariableValueFromObject(testPlugin, PARAM_TABLE_AUDIT_SUFFIX);
        assertEquals("_AUD", auditTableSuffix);

        String filenameSuffixCreate = (String) getVariableValueFromObject(testPlugin, PARAM_FILENAME_SUFFIX_CREATE);
        assertEquals("-create.ddl", filenameSuffixCreate);
        String filenameSuffixDrop = (String) getVariableValueFromObject(testPlugin, PARAM_FILENAME_SUFFIX_DROP);
        assertEquals("-drop.ddl", filenameSuffixDrop);
    }

    public void testGenerateDDLAssertExecute()
            throws Exception {

        DatabaseSchemaGenerator testPlugin = (DatabaseSchemaGenerator) lookupMojo("generate-ddl", testPomFile);
        testPlugin.execute();
        // validate output
        File outputDirectory = (File) getVariableValueFromObject(testPlugin, PARAM_OUTPUT_DIR);
        List<String> dialects = (List<String>) getVariableValueFromObject(testPlugin, PARAM_DIALECTS);
        String filenameSuffixCreate = (String) getVariableValueFromObject(testPlugin, PARAM_FILENAME_SUFFIX_CREATE);
        String filenameSuffixDrop = (String) getVariableValueFromObject(testPlugin, PARAM_FILENAME_SUFFIX_DROP);

        for (String dialect : dialects) {
            String dbName = dialect.substring(dialect.lastIndexOf('.') + 1, dialect.lastIndexOf("Dialect")).toLowerCase();
            assertTrue(new File(outputDirectory, dbName + filenameSuffixCreate).exists());
            assertTrue(new File(outputDirectory, dbName + filenameSuffixDrop).exists());
        }
    }

    public void tesGenerateComment() {
        DatabaseSchemaGenerator testInstance = new DatabaseSchemaGenerator();
        testInstance.application = "application-01";
        testInstance.schemaVersion = "version-01";
        testInstance.generatedOn = "01.12.2022";
        testInstance.commentTemplate = "Script version: ${schemaVersion}, Application: ${application}, Date: ${generatedOn}";
        String result = testInstance.getInitialComment();
        assertEquals("Script version: version-01, Application: application-01, Date: 01.12.2022", result);
    }


    public static PluginDescriptor loadPluginDescriptor(Plugin plugin, MavenSession session)
            throws Exception {

        BuildPluginManager pluginManager = new DefaultBuildPluginManager();
        return pluginManager.loadPlugin(plugin, session.getCurrentProject().getRemotePluginRepositories(),
                session.getRepositorySession());

    }

}

