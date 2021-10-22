package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

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

	private static final Color[] ALL_COLORS = { Color.COLORLESS, Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN, null };

	private static class CardSummaryData {
		public final HashMap<Integer, HashMap<Color, AtomicInteger>> byCmc;
		public final HashMap<Color, AtomicInteger> byColor;
		public volatile double totalCmc;
		public final Object totalCmcLock;
		public final AtomicInteger count;

		public CardSummaryData() {
			byCmc = new HashMap<>();

			byColor = new HashMap<>();
			for (Color color : ALL_COLORS) {
				byColor.put(color, new AtomicInteger());
			}

			totalCmc = 0.0;
			totalCmcLock = new Object();
			count = new AtomicInteger();
		}

		public double averageCmc() {
			return totalCmc / count.get();
		}

		static Collector<CardInstance, CardSummaryData, CardSummaryData> COLLECTOR = new Collector<CardInstance, CardSummaryData, CardSummaryData>() {
			@Override
			public Supplier<CardSummaryData> supplier() {
				return CardSummaryData::new;
			}

			@Override
			public BiConsumer<CardSummaryData, CardInstance> accumulator() {
				return (csd, ci) -> {
					ManaCost mc = ci.card().manaCost();

					HashMap<Color, AtomicInteger> byCmc = csd.byCmc.computeIfAbsent((int) mc.value(), cmc -> {
						HashMap<Color, AtomicInteger> ret = new HashMap<>();
						for (Color color : ALL_COLORS) {
							ret.put(color, new AtomicInteger());
						}
						return ret;
					});

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
					byCmc.get(key).incrementAndGet();

					for (ManaSymbol sym : mc.symbols()) {
						switch (sym.color().size()) {
							case 0:
								continue;
							case 1:
								key = sym.color().iterator().next();
								break;
							default:
								key = null;
								break;
						}

						csd.byColor.get(key).incrementAndGet();
					}

					synchronized(csd.totalCmcLock) {
						csd.totalCmc += mc.value();
					}
					csd.count.incrementAndGet();
				};
			}

			@Override
			public BinaryOperator<CardSummaryData> combiner() {
				return (csd1, csd2) -> {
					CardSummaryData out = new CardSummaryData();

					out.totalCmc = 0.0;
					synchronized (csd1.totalCmcLock) {
						out.totalCmc += csd1.totalCmc;
					}
					synchronized (csd2.totalCmcLock) {
						out.totalCmc += csd1.totalCmc + csd2.totalCmc;
					}

					out.count.set(csd1.count.get() + csd2.count.get());

					for (Color color : ALL_COLORS) {
						out.byColor.get(color).set(csd1.byColor.get(color).get() + csd2.byColor.get(color).get());
					}

					Set<Integer> keys = new HashSet<>();
					keys.addAll(csd1.byCmc.keySet());
					keys.addAll(csd2.byCmc.keySet());

					for (Integer cmc : keys) {
						HashMap<Color, AtomicInteger> byColor = new HashMap<>();

						HashMap<Color, AtomicInteger> csd1ByColor = csd1.byCmc.get(cmc);
						HashMap<Color, AtomicInteger> csd2ByColor = csd2.byCmc.get(cmc);

						for (Color color : ALL_COLORS) {
							AtomicInteger i = new AtomicInteger();

							if (csd1ByColor != null) {
								i.addAndGet(csd1ByColor.get(color).get());
							}

							if (csd2ByColor != null) {
								i.addAndGet(csd2ByColor.get(color).get());
							}

							byColor.put(color, i);
						}

						out.byCmc.put(cmc, byColor);
					}

					return out;
				};
			}

			@Override
			public Function<CardSummaryData, CardSummaryData> finisher() {
				return csd -> csd;
			}

			@Override
			public Set<Characteristics> characteristics() {
				// Not far from CONCURRENT.
				return EnumSet.of(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
			}
		};
	}

	public DeckStatsDialog(ObservableList<CardInstance> cardList) throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("DeckStatsDialog.fxml"));
		loader.setRoot(this);
		loader.setController(this);
		loader.load();
		getDialogPane().setStyle("-fx-base: " + Preferences.get().theme.baseHex());

		// TODO: Update data dynamically.
		CardSummaryData data = cardList.parallelStream().collect(CardSummaryData.COLLECTOR);

		setupCmcChart(data);
		setupPieChart(data);
	}

	private void setupCmcChart(CardSummaryData csd) {
		averageCmc.setText(String.format("Average converted mana cost: %.2f", csd.averageCmc()));

		for (Color c : ALL_COLORS) {
			XYChart.Series<String, Number> series = new XYChart.Series<>(c == null ? "Multicolored" : c.name, FXCollections.observableArrayList());

			for (Map.Entry<Integer, HashMap<Color, AtomicInteger>> entry : csd.byCmc.entrySet()) {
				XYChart.Data<String, Number> datum = new XYChart.Data<>(Integer.toString(entry.getKey()), entry.getValue().get(c).get());
				series.getData().add(datum);
				//datum.setExtraValue(c);
			}

			cmcChart.getData().add(series);

			for (XYChart.Data<String, Number> datum : series.getData()) {
				datum.getNode().setStyle(String.format("-fx-bar-fill: %s", valueOf(CHART_COLORS.get(c))));
			}
		}
	}

	private void setupPieChart(CardSummaryData csd) {
		for (Map.Entry<Color, AtomicInteger> entry : csd.byColor.entrySet()) {
			if (entry.getValue().get() == 0) {
				continue;
			}

			PieChart.Data datum = new PieChart.Data(String.format("%s: %d", entry.getKey() == null ? "Multicolor" : entry.getKey().name, entry.getValue().get()), entry.getValue().get());
			manaSymbolPieChart.getData().add(datum);
			datum.getNode().setStyle(String.format("-fx-pie-color: %s", valueOf(CHART_COLORS.get(entry.getKey()))));
		}
	}
}
