package emi.mtg.deckbuilder.view.components;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public class ProgressTextBar extends StackPane {
	public final ProgressBar bar;
	public final Text text;

	public ProgressTextBar(double progress, String text) {
		super();

		this.bar = new ProgressBar(progress);
		this.bar.setMaxWidth(Double.MAX_VALUE);
		this.bar.setMaxHeight(Double.MAX_VALUE);

		this.text = new Text(text);
		this.text.setFill(Color.WHITE);
		this.text.setTextAlignment(TextAlignment.CENTER);

		setPrefHeight(this.text.prefHeight(100.0) + 10.0);

		getChildren().setAll(this.bar, this.text);
	}

	public ProgressTextBar(double progress) {
		this(progress, "");
	}

	public ProgressTextBar() {
		this(0.0, "");
	}

	public void setTooltip(Tooltip tooltip) {
		this.bar.setTooltip(tooltip);
	}

	public DoubleProperty progressProperty() {
		return bar.progressProperty();
	}

	public StringProperty textProperty() {
		return text.textProperty();
	}

	public void set(double progress, String message) {
		if (!Double.isNaN(progress)) this.bar.setProgress(progress);
		if (message != null && !message.isEmpty()) this.text.setText(message);
	}

	public void setProgress(double progress) {
		this.bar.setProgress(progress);
	}

	public void setText(String text) {
		this.text.setText(text);
	}
}
