package com.shadowcrypt.ui.navigation;

import com.shadowcrypt.ui.theme.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Left sidebar with one button per section. */
public final class NavigationPane extends VBox {

    private final Map<NavigationItem, Button> buttons = new EnumMap<>(NavigationItem.class);
    private NavigationItem selected;

    public NavigationPane(Consumer<NavigationItem> onSelect) {
        setSpacing(6);
        setPadding(new Insets(22, 14, 18, 14));
        setPrefWidth(238);
        setMinWidth(238);
        getStyleClass().add(Theme.SIDEBAR);

        Label brand = new Label("ShadowCrypt");
        brand.getStyleClass().add(Theme.TITLE);

        Label tagline = new Label("offline cryptography\n+ PNG steganography");
        tagline.getStyleClass().add(Theme.SUBTITLE);

        VBox header = new VBox(4, brand, tagline);
        header.setPadding(new Insets(0, 8, 18, 8));
        getChildren().add(header);

        for (NavigationItem item : NavigationItem.values()) {
            Button button = new Button(item.title() + "\n" + item.subtitle());
            button.getStyleClass().add(Theme.NAV_BUTTON);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(event -> select(item, onSelect));
            buttons.put(item, button);
            getChildren().add(button);
        }

        applySelection(NavigationItem.ENCRYPT);
    }

    private void select(NavigationItem item, Consumer<NavigationItem> onSelect) {
        if (selected == item) {
            return;
        }
        applySelection(item);
        onSelect.accept(item);
    }

    private void applySelection(NavigationItem item) {
        selected = item;
        buttons.forEach((key, button) -> button.getStyleClass().setAll(Theme.NAV_BUTTON));
        buttons.get(item).getStyleClass().setAll(Theme.NAV_BUTTON, Theme.NAV_BUTTON_ACTIVE);
    }
}
