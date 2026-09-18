package gg.grumble.client.services;

import gg.grumble.client.components.LocaleProvider;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LanguageService {
    private static final String BUNDLE_NAME = "messages";

    private final LocaleProvider localeProvider;

    public LanguageService(LocaleProvider localeProvider) {
        this.localeProvider = localeProvider;
    }

    /** Looks up {@code key} for the current locale, falling back to the key itself when it has no translation. */
    public String t(String key, Object... args) {
        Locale locale = localeProvider.getLocale();
        String pattern;
        try {
            pattern = ResourceBundle.getBundle(BUNDLE_NAME, locale).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
