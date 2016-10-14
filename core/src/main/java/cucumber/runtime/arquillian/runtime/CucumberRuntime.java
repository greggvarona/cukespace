package cucumber.runtime.arquillian.runtime;

import cucumber.runtime.Backend;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.StepDefinitionMatch;
import cucumber.runtime.arquillian.api.event.*;
import cucumber.runtime.arquillian.shared.EventHelper;
import cucumber.runtime.io.ResourceLoader;
import gherkin.I18n;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;

import java.util.Collection;
import java.util.Set;

public class CucumberRuntime extends cucumber.runtime.Runtime {
    public CucumberRuntime(ResourceLoader resourceLoader,
                           ClassLoader classLoader,
                           Collection<? extends Backend> backends,
                           RuntimeOptions runtimeOptions) {
        super(resourceLoader, classLoader, backends, runtimeOptions);
    }

    @Override
    public void runStep(final String featurePath, final Step step, final Reporter reporter, final I18n i18n) {
        super.runStep(featurePath, step, new Reporter() {
            @Override
            public void match(final Match match) { // lazy to get the method and instance
                if (StepDefinitionMatch.class.isInstance(match)) {
                    EventHelper.matched(StepDefinitionMatch.class.cast(match));
                    EventHelper.fire(new BeforeStep(featurePath, step));
                }
                reporter.match(match);
            }

            @Override
            public void before(final Match match, final Result result) {
                reporter.before(match, result);
            }

            @Override
            public void result(final Result result) {
                reporter.result(result);
            }

            @Override
            public void after(final Match match, final Result result) {
                reporter.after(match, result);
            }

            @Override
            public void embedding(final String mimeType, final byte[] data) {
                reporter.embedding(mimeType, data);
            }

            @Override
            public void write(final String text) {
                reporter.write(text);
            }
        }, i18n);
        EventHelper.fire(new AfterStep(featurePath, step));
    }

    @Override
    public void runBeforeHooks(final Reporter reporter, final Set<Tag> tags) {
        EventHelper.fire(new BeforeBeforeHooks());
        super.runBeforeHooks(reporter, tags);
        EventHelper.fire(new AfterBeforeHooks());
    }

    @Override
    public void runAfterHooks(final Reporter reporter, final Set<Tag> tags) {
        EventHelper.fire(new BeforeAfterHooks());
        super.runAfterHooks(reporter, tags);
        EventHelper.fire(new AfterAfterHooks());
    }
}

