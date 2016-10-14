package cucumber.runtime.arquillian.resource;

import cucumber.runtime.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class URLResource implements Resource {
    private final URL url;
    private final String path;

    public URLResource(final String path, final URL url) {
        this.url = url;
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getAbsolutePath() {
        return url.toExternalForm();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return url.openStream();
    }

    @Override
    public String getClassName(final String extension) {
        final String path = getPath();
        return path.substring(0, path.length() - extension.length()).replace('/', '.');
    }

}
