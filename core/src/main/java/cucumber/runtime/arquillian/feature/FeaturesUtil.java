package cucumber.runtime.arquillian.feature;

import cucumber.api.CucumberOptions;
import cucumber.runtime.FeatureBuilder;
import cucumber.runtime.arquillian.api.Tags;
import cucumber.runtime.arquillian.resource.ClassLoaderResource;
import cucumber.runtime.arquillian.resource.URLResource;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.PathWithLines;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class FeaturesUtil {

    private FeaturesUtil() {}

    public static List<Object> createFilters(final Object testInstance) {
        final List<Object> filters = new ArrayList<Object>();

        final Class<?> testInstanceClass = testInstance.getClass();

        { // our API
            final Tags tags = testInstanceClass.getAnnotation(Tags.class);
            if (tags != null) {
                filters.addAll(Arrays.asList(tags.value()));
            }
        }

        { // cucumber-junit
            final CucumberOptions options = testInstanceClass.getAnnotation(CucumberOptions.class);
            if (options != null) {
                if (options.tags().length > 0) {
                    filters.addAll(Arrays.asList(options.tags()));
                }
                if (options.name().length > 0) {
                    for (final String name : options.name()) {
                        filters.add(Pattern.compile(name));
                    }
                }
            }
        }

        return filters;
    }

    public static List<CucumberFeature> buildFeatureList(final Set<Object> testFilters, final InputStream featuresInputStream, final ClassLoader classLoader, final Map<String, Collection<URL>> featuresMap) throws Exception {
        final List<CucumberFeature> cucumberFeatures = new ArrayList<CucumberFeature>();
        final FeatureBuilder featureBuilder = new FeatureBuilder(cucumberFeatures);

        if (featuresInputStream != null) {

            buildFeatureListFromFile(featuresInputStream, testFilters, featureBuilder, classLoader);
        } else {
            buildFeatureListFromMap(featuresMap, testFilters, featureBuilder);
        }

        featureBuilder.close();

        if (cucumberFeatures.isEmpty()) {
            throw new IllegalArgumentException("No feature found");
        }

        return cucumberFeatures;
    }

    private static void buildFeatureListFromFile(final InputStream featuresInputStream, final Set<Object> testFilters, final FeatureBuilder featureBuilder, final ClassLoader classLoader) throws Exception {
        final BufferedReader featuresFileReader = new BufferedReader(new InputStreamReader(featuresInputStream));

        String readerLine;

        while ((readerLine = featuresFileReader.readLine()) != null) {
            readerLine = readerLine.trim();
            if (readerLine.isEmpty()) {
                continue;
            }

            final PathWithLines pathWithLines = new PathWithLines(readerLine);
            testFilters.addAll(pathWithLines.lines);
            featureBuilder.parse(new ClassLoaderResource(classLoader, pathWithLines.path), new ArrayList<Object>(testFilters));
        }

        featuresFileReader.close();
    }

    private static void buildFeatureListFromMap(final Map<String, Collection<URL>> featuresMap, final Set<Object> testFilters, final FeatureBuilder featureBuilder) {
        final Set<Map.Entry<String, Collection<URL>>> featuresEntriesSet = featuresMap.entrySet();

        for (final Map.Entry<String, Collection<URL>> entry : featuresEntriesSet) {
            final PathWithLines pathWithLines = new PathWithLines(entry.getKey());
            testFilters.addAll(pathWithLines.lines);
            for (final URL url : entry.getValue()) {
                featureBuilder.parse(new URLResource(pathWithLines.path, url), new ArrayList<Object>(testFilters));
            }
        }
    }
}
