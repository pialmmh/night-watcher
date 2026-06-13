package com.tb.nw.core.config.internal;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.yaml.snakeyaml.Yaml;

/**
 * SmallRye config source that loads the active tenant / profile YAML from
 * the classpath at agent startup.
 *
 * <p>Reads the values of {@code nw.tenant.name} and {@code nw.tenant.profile}
 * (already supplied by {@code application.properties} or by env override)
 * and registers the YAML at
 * {@code config/tenants/<name>/<profile>/profile-<profile>.yml} as a
 * config source whose priority sits <em>between</em> the static properties
 * file and env / system overrides — so:</p>
 *
 * <ol>
 *   <li>Defaults baked into the SPI / nw-core classes (lowest)</li>
 *   <li>{@code application.properties}</li>
 *   <li>The tenant-profile YAML <strong>(this source)</strong></li>
 *   <li>System properties</li>
 *   <li>Environment variables (highest)</li>
 * </ol>
 *
 * <p>This is how routesphere-core lets a single binary serve multiple
 * tenants and profiles without rebuilding — pick the active tenant via
 * env, the rest of the config comes from the right YAML. Quarkus's
 * {@code quarkus.config.locations} can't do this directly because it
 * doesn't accept {@code classpath:} URIs at build time.</p>
 */
public class TenantProfileConfigSource implements ConfigSourceFactory {

    /** Priority sits above PropertiesConfigSource (default 100) but below env/sys (300+). */
    private static final int ORDINAL = 250;

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        String tenant = readKey(context, "nw.tenant.name");
        String profile = readKey(context, "nw.tenant.profile");
        if (tenant == null || profile == null) {
            // Nothing to load — agent will fall back to whatever the properties
            // file and env supply.
            return Collections.emptyList();
        }

        String resourcePath = "config/tenants/" + tenant + "/" + profile + "/profile-" + profile + ".yml";

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = TenantProfileConfigSource.class.getClassLoader();

        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Profile YAML missing — emit a single "marker" entry so an
                // operator can see in the agent log that the lookup happened
                // and what it expected.
                Map<String, String> marker = Map.of(
                        "nw.tenant.profile-yaml.missing", resourcePath);
                return List.of(new PropertiesConfigSource(marker, "tenant-profile(missing:" + resourcePath + ")", ORDINAL));
            }
            Map<String, String> flat = flattenYaml(new Yaml().load(in));
            flat.put("nw.tenant.profile-yaml.loaded", resourcePath);
            return List.of(new PropertiesConfigSource(flat, "tenant-profile(" + resourcePath + ")", ORDINAL));
        } catch (Exception e) {
            Map<String, String> marker = Map.of(
                    "nw.tenant.profile-yaml.error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return List.of(new PropertiesConfigSource(marker, "tenant-profile(error)", ORDINAL));
        }
    }

    @Override
    public OptionalInt getPriority() {
        // Run after the static properties source so we can read
        // nw.tenant.name / nw.tenant.profile from application.properties.
        return OptionalInt.of(200);
    }

    // ── helpers ──

    private static String readKey(ConfigSourceContext ctx, String key) {
        var v = ctx.getValue(key);
        return v == null ? null : v.getValue();
    }

    /** Flatten a SnakeYAML-parsed object (Maps / Lists / scalars) into dotted keys. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> flattenYaml(Object root) {
        Map<String, String> out = new LinkedHashMap<>();
        if (root instanceof Map<?, ?> map) {
            walk("", (Map<String, Object>) map, out);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String prefix, Map<String, Object> node, Map<String, String> out) {
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v == null) {
                // skip null values
            } else if (v instanceof Map<?, ?> m) {
                walk(key, (Map<String, Object>) m, out);
            } else if (v instanceof List<?> list) {
                // Quarkus / SmallRye list binding accepts both indexed
                // (key[0]=…) and comma-separated. Write both so either
                // @ConfigMapping or @ConfigProperty(name="key") works.
                StringBuilder csv = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String s = item == null ? "" : item.toString();
                    out.put(key + "[" + i + "]", s);
                    if (i > 0) csv.append(",");
                    csv.append(s);
                }
                out.put(key, csv.toString());
            } else {
                out.put(key, String.valueOf(v));
            }
        }
    }
}
