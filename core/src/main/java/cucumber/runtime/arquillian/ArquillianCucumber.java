package cucumber.runtime.arquillian;

import cucumber.runtime.CucumberException;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.arquillian.backend.ArquillianBackend;
import cucumber.runtime.arquillian.config.CucumberConfiguration;
import cucumber.runtime.arquillian.feature.Features;
import cucumber.runtime.arquillian.reporter.CucumberReporter;
import cucumber.runtime.arquillian.runtime.CucumberRuntime;
import cucumber.runtime.arquillian.shared.ClientServerFiles;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import gherkin.formatter.JSONFormatter;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

import static cucumber.runtime.arquillian.feature.FeaturesUtil.buildFeatureList;
import static cucumber.runtime.arquillian.feature.FeaturesUtil.createFilters;
import static cucumber.runtime.arquillian.glue.GluesUtil.loadGlues;
import static cucumber.runtime.arquillian.runtime.RuntimeOptionsUtil.loadRuntimeOptions;
import static java.util.Collections.singletonList;

public class ArquillianCucumber extends Arquillian {
    private static final Logger LOGGER = Logger.getLogger(ArquillianCucumber.class.getName());

    private static final String RUN_CUCUMBER_MTD = "performInternalCucumberOperations";

    private List<FrameworkMethod> methods;

    public ArquillianCucumber(final Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected Description describeChild(final FrameworkMethod method) {
        if (!Boolean.getBoolean("cukespace.runner.standard-describe")
                && InstanceControlledFrameworkMethod.class.isInstance(method)) {
            return Description.createTestDescription(
                    InstanceControlledFrameworkMethod.class.cast(method).getOriginalClass(),
                    ArquillianCucumber.RUN_CUCUMBER_MTD,
                    method.getAnnotations());
        }
        return super.describeChild(method);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        if (methods != null) {
            return methods;
        }

        methods = new LinkedList<FrameworkMethod>();

        // run @Test methods
        for (final FrameworkMethod each : ArquillianCucumber.super.computeTestMethods()) {
            methods.add(each);
        }

        try { // run cucumber, this looks like a hack but that's to keep @Before/@After/... hooks behavior
            final Method runCucumber = ArquillianCucumber.class.getDeclaredMethod(RUN_CUCUMBER_MTD, Object.class, RunNotifier.class);
            methods.add(new InstanceControlledFrameworkMethod(this, getTestClass().getJavaClass(), runCucumber));
        } catch (final NoSuchMethodException e) {
            // no-op: will not accur...if so this exception is not your biggest issue
        }

        return methods;
    }

    @Override
    protected void runChild(final FrameworkMethod method, final RunNotifier notifier) {
        if (InstanceControlledFrameworkMethod.class.isInstance(method)) {
            InstanceControlledFrameworkMethod.class.cast(method).setNotifier(notifier);
        }
        super.runChild(method, notifier);
    }

    // the cucumber test method, only used internally - see childrenInvoker, public to avoid to setAccessible(true)
    public void performInternalCucumberOperations(final Object testInstance, final RunNotifier runNotifier) throws Exception {

        final Class<?> javaTestClass = getTestClass().getJavaClass();
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        final InputStream configurationInputStream = classLoader.getResourceAsStream(ClientServerFiles.CONFIG);
        final Properties cukespaceConfigurationProperties = loadCucumberConfigurationProperties(configurationInputStream);

        final Map<String, Collection<URL>> featuresMap = Features.createFeatureMap(CucumberConfiguration.instance().getTempDir(), cukespaceConfigurationProperties.getProperty(CucumberConfiguration.FEATURE_HOME), javaTestClass, classLoader);
        final List<CucumberFeature> cucumberFeatures = getCucumberFeatures(testInstance, classLoader, featuresMap);

        final RuntimeOptions runtimeOptions = loadRuntimeOptions(javaTestClass, cukespaceConfigurationProperties);

        final boolean reported = Boolean.parseBoolean(cukespaceConfigurationProperties.getProperty(CucumberConfiguration.REPORTABLE, "false"));
        final StringBuilder reportBuilder = new StringBuilder();
        if (reported) {
            runtimeOptions.addPlugin(new JSONFormatter(reportBuilder));
        }

        final InputStream gluesInputStream = classLoader.getResourceAsStream(ClientServerFiles.GLUES_LIST);
        final Collection<Class<?>> glues = loadGlues(gluesInputStream, classLoader, javaTestClass);

        final ArquillianBackend arquillianBackend = new ArquillianBackend(glues, javaTestClass, testInstance);
        final CucumberRuntime cucumberRuntime = new CucumberRuntime(null, classLoader, singletonList(arquillianBackend), runtimeOptions);
        final Formatter formatter = runtimeOptions.formatter(classLoader);
        final JUnitReporter jUnitReporter = new JUnitReporter(
                runtimeOptions.reporter(classLoader), formatter,
                runtimeOptions.isStrict(), new JUnitOptions(runtimeOptions.getJunitOptions()));
        runFeatures(cucumberFeatures, cucumberRuntime, jUnitReporter, runNotifier);

        if (reported) {
            final String path = cukespaceConfigurationProperties.getProperty(CucumberConfiguration.REPORTABLE_PATH);
            addReportTestIntoFile(path, javaTestClass, reportBuilder);
        }

        handleCucumberTestErrors(cucumberRuntime.getErrors(), cucumberRuntime);

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

    private void runFeatures(final List<CucumberFeature> cucumberFeatures, final CucumberRuntime cucumberRuntime, final JUnitReporter jUnitReporter, final RunNotifier runNotifier) throws Exception {
        for (final CucumberFeature feature : cucumberFeatures) {
            LOGGER.info("Running " + feature.getPath());
            new FeatureRunner(feature, cucumberRuntime, jUnitReporter).run(runNotifier);
        }
        jUnitReporter.done();
        jUnitReporter.close();
        cucumberRuntime.printSummary();
    }

    private static void addReportTestIntoFile(final String path, final Class<?> javaTestClass, final StringBuilder reportBuilder) throws Exception {
        if (path != null) {
            final File destination = CucumberConfiguration.reportFile(path, javaTestClass);
            final File parentFile = destination.getParentFile();
            if (!parentFile.exists() && !parentFile.mkdirs()) {
                throw new IllegalArgumentException("Can't create " + parentFile.getAbsolutePath());
            }

            FileWriter writer = null;
            try {
                writer = new FileWriter(destination);
                writer.write(reportBuilder.toString());
                writer.flush();
            } catch (final IOException e) {
                if (writer != null) {
                    writer.close();
                }
            }

            // add it here too for client case
            CucumberReporter.addReport(CucumberConfiguration.reportFile(path, javaTestClass));
        }
    }

    public void handleCucumberTestErrors(final List<Throwable> errors, CucumberRuntime cucumberRuntime) throws Exception {
        for (final String snippet : cucumberRuntime.getSnippets()) {
            errors.add(new CucumberException("Missing snippet: " + snippet));
        }
        if (!errors.isEmpty()) {
            throw new MultipleFailureException(errors);
        }
    }

    private static class InstanceControlledFrameworkMethod extends FrameworkMethod {
        private final ArquillianCucumber instance;
        private final Class<?> originalClass;
        private RunNotifier notifier;

        public InstanceControlledFrameworkMethod(final ArquillianCucumber runner, final Class<?> originalClass, final Method runCucumber) {
            super(runCucumber);
            this.originalClass = originalClass;
            this.instance = runner;
        }

        @Override
        public Object invokeExplosively(final Object target, final Object... params) throws Throwable {
            instance.performInternalCucumberOperations(target, notifier == null ? new RunNotifier() : notifier);
            return null;
        }

        public Class<?> getOriginalClass() {
            return originalClass;
        }

        public void setNotifier(final RunNotifier notifier) {
            this.notifier = notifier;
        }
    }
}
