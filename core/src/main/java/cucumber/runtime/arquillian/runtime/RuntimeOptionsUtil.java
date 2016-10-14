package cucumber.runtime.arquillian.runtime;

import cucumber.api.CucumberOptions;
import cucumber.runtime.Env;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.arquillian.config.CucumberConfiguration;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static java.util.Arrays.asList;

public final class RuntimeOptionsUtil {

    private RuntimeOptionsUtil() {}

    public static RuntimeOptions loadRuntimeOptions(final Class<?> javaTestClass, final Properties cukespaceConfigurationProperties) {
        final RuntimeOptions runtimeOptions;
        if (javaTestClass.getAnnotation(CucumberOptions.class) != null) { // by class setting
            final RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(javaTestClass);
            runtimeOptions = runtimeOptionsFactory.create();
            cleanClasspathList(runtimeOptions.getGlue());
            cleanClasspathList(runtimeOptions.getFeaturePaths());
        } else if (cukespaceConfigurationProperties.containsKey(CucumberConfiguration.OPTIONS)) { // arquillian setting
            runtimeOptions = new RuntimeOptions(new Env("cucumber-jvm"), asList((cukespaceConfigurationProperties.getProperty(CucumberConfiguration.OPTIONS, "--strict") + " --strict").split(" ")));
        } else { // default
            runtimeOptions = new RuntimeOptions(new Env("cucumber-jvm"), asList("--strict", "--plugin", "pretty", areColorsNotAvailable(cukespaceConfigurationProperties)));
        }
        return runtimeOptions;
    }

    private static String areColorsNotAvailable(final Properties cukespaceConfig) {
        if (!Boolean.parseBoolean(cukespaceConfig.getProperty("colors", "false"))) {
            return "--monochrome";
        }
        return "--no-monochrome";
    }

    // classpath: doesn't support scanning, it should be done on client side if supported, not server side
    private static void cleanClasspathList(final List<String> list) {
        final Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith("classpath:")) {
                it.remove();
            }
        }
    }
}
