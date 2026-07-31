package com.personal.assistant.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBeanConstructorWiringTest {
    @Test
    void beansWithMultipleConstructorsDeclareExactlyOneAutowiredConstructor() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class, true, true));
        List<String> violations = new ArrayList<>();

        for (var definition : scanner.findCandidateComponents("com.personal.assistant")) {
            Class<?> beanClass = Class.forName(definition.getBeanClassName());
            Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
            if (constructors.length <= 1) continue;
            long autowiredConstructors = List.of(constructors).stream()
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .count();
            if (autowiredConstructors != 1) {
                violations.add(beanClass.getName() + " has " + constructors.length
                        + " constructors and " + autowiredConstructors + " @Autowired constructors");
            }
        }

        assertTrue(violations.isEmpty(), "Spring Bean 多构造器必须明确标记唯一 @Autowired 构造器:\n"
                + String.join("\n", violations));
    }
}
