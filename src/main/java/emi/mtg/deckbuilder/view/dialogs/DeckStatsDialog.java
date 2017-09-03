package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.*;

public class DeckStatsDialog extends Dialog<Void> {
	private static final Map<Color, javafx.scene.paint.Color> CHART_COLORS = colorColorMap();

	private static final Map<Color, javafx.scene.paint.Color> colorColorMap() {
		Map<Color, javafx.scene.paint.Color> ccm = new HashMap<>();

		ccm.put(Color.COLORLESS, javafx.scene.paint.Color.color(0.4, 0.4, 0.4));
		ccm.put(Color.WHITE, javafx.scene.paint.Color.color(0.9, 0.9, 0.9));
		ccm.put(Color.BLUE, javafx.scene.paint.Color.color(0.2, 0.2, 0.9));
		ccm.put(Color.BLACK, javafx.scene.paint.Color.color(0.2, 0.2, 0.2));
		ccm.put(Color.RED, javafx.scene.paint.Color.color(0.9, 0.2, 0.2));
		ccm.put(Color.GREEN, javafx.scene.paint.Color.color(0.2, 0.9, 0.2));
		ccm.put(null, javafx.scene.paint.Color.color(1.0, 0.7, 0.2));

		return Collections.unmodifiableMap(ccm);
	}

	private static String valueOf(javafx.scene.paint.Color color) {
		return String.format("rgb(%.0f, %.0f, %.0f)", color.getRed() * 255, color.getGreen() * 255, color.getBlue() * 255);
	}

	@FXML
	private PieChart manaSymbolPieChart;

	@FXML
	private StackedBarChart<String, Number> cmcChart;

	@FXML
	private Label averageCmc;

	public DeckStatsDialog(List<CardInstance> cardList) throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("DeckStatsDialog.fxml"));
		loader.setRoot(this);
		loader.setController(this);
		loader.load();

		setupCmcChart(cardList);
		setupPieChart(cardList);
	}

	private void setupCmcChart(List<CardInstance> cardList) {
		Map<Integer, Map<Color, Integer>> dataMap = new TreeMap<>();

		double totalCmc = 0;
		double highestCmc = 0;

		for (CardInstance ci : cardList) {
			ManaCost mc = ci.card().manaCost();

			if (mc.symbols().isEmpty() || mc.varies()) {
				continue;
			}

			double cmc = mc.convertedCost();

			totalCmc += mc.convertedCost();

			if (cmc > highestCmc) {
				highestCmc = cmc;
			}

			Map<Color, Integer> perColorMap = dataMap.computeIfAbsent((int) mc.convertedCost(), c -> new HashMap<>());

			Color key;
			switch (mc.color().size()) {
				case 0:
					key = Color.COLORLESS;
					break;
				case 1:
					key = mc.color().iterator().next();
					break;
				default:
					key = null;
					break;
			}

			perColorMap.compute(key, (k, n) -> n == null ? 1 : n + 1);
		}

		for (int i = 0; i <= highestCmc; ++i) {
			dataMap.computeIfAbsent(i, n -> new HashMap<>());
		}

		averageCmc.setText(String.format("Average converted mana cost: %.2f", (totalCmc / cardList.size())));

		for (Color c : Arrays.asList(Color.COLORLESS, Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN, null)) {
			XYChart.Series<String, Number> series = new XYChart.Series<>(c == null ? "Multicolored" : c.name, FXCollections.observableArrayList());

			for (Map.Entry<Integer, Map<Color, Integer>> entry : dataMap.entrySet()) {
				XYChart.Data<String, Number> datum = new XYChart.Data<>(Integer.toString(entry.getKey()), entry.getValue().getOrDefault(c, 0));
				series.getData().add(datum);
				//datum.setExtraValue(c);
			}

			cmcChart.getData().add(series);

			for (XYChart.Data<String, Number> datum : series.getData()) {
				datum.getNode().setStyle(String.format("-fx-bar-fill: %s", valueOf(CHART_COLORS.get(c))));
			}
		}
	}

	private void setupPieChart(List<CardInstance> cardList) {
		Map<Color, Integer> histogram = new EnumMap<>(Color.class);

		for (CardInstance ci : cardList) {
			for (ManaSymbol sym : ci.card().manaCost().symbols()) {
				for (Color c : sym.color()) {
					histogram.compute(c, (x, n) -> n == null ? 1 : n + 1);
				}
			}
		}

		for (Map.Entry<Color, Integer> entry : histogram.entrySet()) {
			PieChart.Data datum = new PieChart.Data(String.format("%s: %d", entry.getKey().name, entry.getValue()), entry.getValue());
			manaSymbolPieChart.getData().add(datum);
			datum.getNode().setStyle(String.format("-fx-pie-color: %s", valueOf(CHART_COLORS.get(entry.getKey()))));
		}
	}
}
