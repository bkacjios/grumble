package gg.grumble.client.components;

import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class PrimaryStageHolder {
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public boolean isFocused() {
        return stage != null && stage.isFocused();
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public boolean isIconified() {
        return stage != null && stage.isIconified();
    }
}
