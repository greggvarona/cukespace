package cucumber.runtime.arquillian.resource;

import cucumber.runtime.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ClassLoaderResource implements Resource {
    private final String path;
    private final ClassLoader loader;

    public ClassLoaderResource(final ClassLoader loader, final String path) {
        this.path = path;
        this.loader = loader;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getAbsolutePath() {
        final URL resource = loader.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException(path + " doesn't exist");
        }
        return resource.toExternalForm();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final URL resource = loader.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException(path + " doesn't exist");
        }
        return resource.openStream();
    }

    @Override
    public String getClassName(final String extension) {
        final String path = getPath();
        return path.substring(0, path.length() - extension.length()).replace('/', '.');
    }
}
