package gg.grumble.client.components;

import java.util.Locale;

public class LocaleProvider {
    private volatile Locale current = Locale.ENGLISH;

    public Locale getLocale() {
        return current;
    }

    public void setLocale(Locale locale) {
        this.current = locale;
    }
}
