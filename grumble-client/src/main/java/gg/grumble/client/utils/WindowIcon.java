package gg.grumble.client.utils;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WindowIcon {
    String value(); // path to the icon, relative to resources
}
