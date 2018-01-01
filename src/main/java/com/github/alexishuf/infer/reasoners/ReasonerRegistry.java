package com.github.alexishuf.infer.reasoners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class ReasonerRegistry {
    private static Logger logger = LoggerFactory.getLogger(ReasonerRegistry.class);
    private static Map<String, Class<? extends SplitReasoner>> map;

    public static void register(@Nonnull Class<? extends SplitReasoner> aClass) {
        map.put(aClass.getAnnotation(ReasonerName.class).value(), aClass);
    }

    public static @Nonnull Set<String> getNames() {
        return map.keySet();
    }

    public static @Nonnull
    SplitReasoner getReasoner(@Nonnull String name) {
        Class<? extends SplitReasoner> aClass = map.getOrDefault(name, null);
        if (aClass == null) {
            throw  new NoSuchElementException(String.format(
                    "No implementation registered for %s", name));
        }

        try {
            Constructor<? extends SplitReasoner> constructor = aClass.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException
                |InstantiationException e) {
            logger.error("Failed to instantiate implementation {} for {} reasoner.",
                    aClass.getName(), name, e);
            throw new RuntimeException(e);
        }
    }

    static {
        map = new HashMap<>();
        register(JenaSplitReasoner.class);
    }
}
