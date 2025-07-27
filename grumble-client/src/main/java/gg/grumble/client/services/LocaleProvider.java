package gg.grumble.client.services;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LocaleProvider {
    private volatile Locale current = Locale.ENGLISH;

    public Locale getLocale() {
        return current;
    }

    public void setLocale(Locale locale) {
        this.current = locale;
    }
}
