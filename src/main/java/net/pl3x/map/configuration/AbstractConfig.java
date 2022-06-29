package net.pl3x.map.configuration;

import net.pl3x.map.logger.Logger;
import net.pl3x.map.util.FileUtil;
import org.apache.commons.lang.StringEscapeUtils;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class AbstractConfig {
    public static final Path DATA_DIR = FileUtil.PLUGIN_DIR.resolve("data");
    public static final Path LOCALE_DIR = FileUtil.PLUGIN_DIR.resolve("locale");

    private YamlConfiguration config;

    public YamlConfiguration getConfig() {
        return this.config;
    }

    public void reload(Path path, Class<? extends AbstractConfig> clazz) {
        this.config = new YamlConfiguration();

        getConfig().options().copyDefaults(true);
        getConfig().options().parseComments(true);
        getConfig().options().width(9999);

        File file = path.toFile();
        String filename = path.getFileName().toString();

        try {
            getConfig().load(file);
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException e) {
            Logger.severe("Could not load " + filename + ", please correct your syntax errors");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        Arrays.stream(clazz.getDeclaredFields()).forEach(field -> {
            Key key = field.getDeclaredAnnotation(Key.class);
            Comment comment = field.getDeclaredAnnotation(Comment.class);
            if (key == null) {
                return;
            }
            try {
                Object classObj = getClassObject();
                Object value = getValue(key.get(), field.get(classObj));
                field.set(classObj, value instanceof String str ? StringEscapeUtils.unescapeJava(str) : value);
            } catch (IllegalAccessException e) {
                Logger.warn("Failed to load " + filename);
                e.printStackTrace();
            }
            if (comment != null) {
                setComments(key.get(), Arrays.stream(comment.get().split("\n")).toList());
            }
        });

        try {
            getConfig().save(file);
        } catch (IOException e) {
            Logger.severe("Could not save " + path);
            e.printStackTrace();
        }
    }

    protected Object getClassObject() {
        return null;
    }

    protected Object getValue(String path, Object def) {
        if (getConfig().get(path) == null) {
            getConfig().set(path, def);
        }
        return getConfig().get(path);
    }

    protected void setComments(String path, List<String> comments) {
        getConfig().setComments(path, comments);
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Key {
        String get();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Comment {
        String get();
    }
}
