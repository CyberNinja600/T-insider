package com.tinsider.ui.components.toolbar;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Skin;

/**
 * A ComboBox control that renders a searchable popup matching the design specification.
 * When clicked, it displays a search input at the top and real-time filtered options below.
 *
 * @param <T> the type of elements in the combo box
 */
public class SearchableComboBox<T> extends ComboBox<T> {

    public SearchableComboBox() {
        super();
    }

    public SearchableComboBox(ObservableList<T> items) {
        super(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SearchableComboBoxSkin<>(this);
    }
}
