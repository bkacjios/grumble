package gg.grumble.client.services;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LinkUrlService {

    private static final String URL_REGEX = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);

    public String linkifyUrls(String message) {
        Matcher matcher = URL_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(1);
            String href = String.format("<a href=\"%s\">%s</a>", url, url);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(href));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String cleanHtml(String input) {
        // Remove HTML tags
        String noTags = input.replaceAll("(?s)<[^>]*>", "");
        // Decode HTML entities to actual characters
        return StringEscapeUtils.unescapeHtml4(noTags);
    }
}
