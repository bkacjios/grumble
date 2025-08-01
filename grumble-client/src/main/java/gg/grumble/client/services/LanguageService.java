package gg.grumble.client.services;

import gg.grumble.client.components.LocaleProvider;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class LanguageService {
    private final MessageSource messageSource;
    private final LocaleProvider localeProvider;

    public LanguageService(MessageSource messageSource, LocaleProvider localeProvider) {
        this.messageSource = messageSource;
        this.localeProvider = localeProvider;
    }

    public String t(String key, Object... args) {
        return messageSource.getMessage(key, args, localeProvider.getLocale());
    }
}
