package cucumber.runtime.arquillian;

import cucumber.api.testng.CucumberExceptionWrapper;
import cucumber.api.testng.CucumberFeatureWrapper;
import cucumber.api.testng.CucumberFeatureWrapperImpl;
import cucumber.api.testng.FeatureResultListener;
import cucumber.runtime.CucumberException;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.arquillian.backend.ArquillianBackend;
import cucumber.runtime.arquillian.config.CucumberConfiguration;
import cucumber.runtime.arquillian.feature.Features;
import cucumber.runtime.arquillian.runtime.CucumberRuntime;
import cucumber.runtime.arquillian.shared.ClientServerFiles;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import gherkin.formatter.JSONFormatter;
import org.jboss.arquillian.testng.Arquillian;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

import static cucumber.runtime.arquillian.feature.FeaturesUtil.buildFeatureList;
import static cucumber.runtime.arquillian.feature.FeaturesUtil.createFilters;
import static cucumber.runtime.arquillian.glue.GluesUtil.loadGlues;
import static cucumber.runtime.arquillian.runtime.RuntimeOptionsUtil.loadRuntimeOptions;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

public class ArquillianTestngCucumber extends Arquillian {
    private static final Logger LOGGER = Logger.getLogger(ArquillianTestngCucumber.class.getName());

    private CucumberRuntime cucumberRuntime;
    private RuntimeOptions runtimeOptions;
    private ResourceLoader resourceLoader;
    private FeatureResultListener resultListener;
    private ClassLoader classLoader;
    private Properties cukespaceConfigurationProperties;
    private boolean reported;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() {
        LOGGER.info("Executing before class.");
        try {
            init();
        } catch (Exception e) {
            LOGGER.warning(e.getMessage());
        }
        LOGGER.info("Done initialization.");
    }

    @Test(groups = "cucumber", description = "Runs Cucumber Feature", dataProvider = "features")
    public void feature(CucumberFeatureWrapper cucumberFeature) {
        LOGGER.info("Executing feature.");
        runFeature(cucumberFeature.getCucumberFeature());
        LOGGER.info("Done executing feature.");
    }

    @AfterClass(groups = "cucumber", alwaysRun = true)
    public void finish() {
        LOGGER.info("Cleaning up.");
        Formatter formatter = runtimeOptions.formatter(classLoader);

        formatter.done();
        formatter.close();
        if (reported) {
            cucumberRuntime.printSummary();
        }
        LOGGER.info("Printing summary.");
    }

    @DataProvider
    public Object[][] features() throws Exception {
        LOGGER.info("Getting the features.");
        return this.provideFeatures();
    }
    /**
     * @return returns the cucumber features as a two dimensional array of
     * {@link CucumberFeatureWrapper} objects.
     */
    public Object[][] provideFeatures() throws Exception {
        try {
            LOGGER.info(format("Getting features in class: %s", this.getClass()));
            final Map<String, Collection<URL>> featuresMap = Features.createFeatureMap(CucumberConfiguration.instance().getTempDir(), cukespaceConfigurationProperties.getProperty(CucumberConfiguration.FEATURE_HOME), this.getClass(), classLoader);
            final List<CucumberFeature> cucumberFeatures = getCucumberFeatures(this, classLoader, featuresMap);

            List<Object[]> featuresList = new ArrayList<Object[]>(cucumberFeatures.size());
            for (CucumberFeature feature : cucumberFeatures) {
                featuresList.add(new Object[]{new CucumberFeatureWrapperImpl(feature)});
            }
            return featuresList.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e)}};
        }
    }
    // the cucumber test method, only used internally - see childrenInvoker, public to avoid to setAccessible(true)
    public void init() throws Exception {
        Class javaTestClass = this.getClass();
        classLoader = Thread.currentThread().getContextClassLoader();
        resourceLoader = new MultiLoader(classLoader);

        final InputStream configurationInputStream = classLoader.getResourceAsStream(ClientServerFiles.CONFIG);
        cukespaceConfigurationProperties = loadCucumberConfigurationProperties(configurationInputStream);

        runtimeOptions = loadRuntimeOptions(javaTestClass, cukespaceConfigurationProperties);
        resultListener = new FeatureResultListener(runtimeOptions.reporter(classLoader), runtimeOptions.isStrict());

        reported = Boolean.parseBoolean(cukespaceConfigurationProperties.getProperty(CucumberConfiguration.REPORTABLE, "false"));
        final StringBuilder reportBuilder = new StringBuilder();
        if (reported) {
            runtimeOptions.addPlugin(new JSONFormatter(reportBuilder));
        }

        final InputStream gluesInputStream = classLoader.getResourceAsStream(ClientServerFiles.GLUES_LIST);
        final Collection<Class<?>> glues = loadGlues(gluesInputStream, classLoader, javaTestClass);

        final ArquillianBackend arquillianBackend = new ArquillianBackend(glues, javaTestClass, this);
        cucumberRuntime = new CucumberRuntime(resourceLoader, classLoader, singletonList(arquillianBackend), runtimeOptions);
    }

    private static Properties loadCucumberConfigurationProperties(final InputStream configurationInputStream) throws Exception {
        if (configurationInputStream != null) {
            return loadConfigurationPropertiesFromStream(configurationInputStream);
        } else {
            return loadConfigurationPropertiesFromObject(CucumberConfiguration.instance());
        }
    }

    private static Properties loadConfigurationPropertiesFromStream(final InputStream configurationInputStream) throws Exception {
        Properties configurationProperties = new Properties();
        configurationProperties.load(configurationInputStream);
        return configurationProperties;
    }

    private static Properties loadConfigurationPropertiesFromObject(final CucumberConfiguration cucumberConfiguration) throws Exception {
        return cucumberConfiguration.getConfigurationAsProperties();
    }

    private static List<CucumberFeature> getCucumberFeatures(final Object testInstance, final ClassLoader classLoader, final Map<String, Collection<URL>> featuresMap) throws Exception {
        final HashSet<Object> testFilters = new HashSet<Object>(createFilters(testInstance));
        final InputStream featuresInputStream = classLoader.getResourceAsStream(ClientServerFiles.FEATURES_LIST);
        return buildFeatureList(testFilters, featuresInputStream, classLoader, featuresMap);
    }

    private void runFeature(CucumberFeature cucumberFeature) {
        resultListener.startFeature();
        cucumberFeature.run(
                    runtimeOptions.formatter(classLoader),
                    resultListener,
                    cucumberRuntime);

        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
    }
}
