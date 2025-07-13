package gg.grumble.client.utils;

import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class UiEventController implements Closeable {

    private final ConfigurableApplicationContext context;

    public UiEventController(ConfigurableApplicationContext context) {
        this.context = context;
    }

    private final List<ApplicationListener<?>> registeredListeners = new ArrayList<>();

    @PostConstruct
    public void initializeEventHooks() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            UiEventListener annotation = method.getAnnotation(UiEventListener.class);
            if (annotation != null) {
                method.setAccessible(true);

                ApplicationListener<?> listener = (ApplicationListener<ApplicationEvent>) event -> {
                    if (annotation.value().isAssignableFrom(event.getClass())) {
                        try {
                            Platform.runLater(() -> {
                                try {
                                    method.invoke(this, event);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };

                context.addApplicationListener(listener);
                registeredListeners.add(listener);
            }
        }
    }

    @Override
    public void close() {
        registeredListeners.forEach(context::removeApplicationListener);
        registeredListeners.clear();
    }
}
