package cucumber.runtime.arquillian.glue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;

public class GluesUtil {

    private GluesUtil() {}

    public static Collection<Class<?>> loadGlues(final InputStream gluesInputStream, final ClassLoader classLoader, final Class<?> javaTestClass) throws Exception {
        final Collection<Class<?>> glues = new LinkedList<Class<?>>();

        if (gluesInputStream != null) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(gluesInputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                glues.add(classLoader.loadClass(line));
            }
            reader.close();

        } else { // client side
            glues.addAll(Glues.findGlues(javaTestClass));
        }

        return glues;
    }
}
