import emi.mtg.deckbuilder.view.search.SearchProvider;
import emi.mtg.deckbuilder.view.search.TextSearch;
import emi.mtg.deckbuilder.view.search.expressions.ExpressionFilter;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.search.omnifilter.filters.ColorFilter;
import emi.mtg.deckbuilder.view.search.omnifilter.filters.PowerToughnessLoyalty;

module emi.mtg.deckbuilder {
	requires emi.lib.mtg;
	requires com.google.gson;
	requires javafx.base;
	requires javafx.graphics;
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.web;
	requires javafx.swing;

	exports emi.mtg.deckbuilder.controller;
	exports emi.mtg.deckbuilder.controller.serdes;
	exports emi.mtg.deckbuilder.model;
	exports emi.mtg.deckbuilder.view;
	exports emi.mtg.deckbuilder.view.groupings;
	exports emi.mtg.deckbuilder.util;

	exports emi.mtg.deckbuilder.view.search;
	exports emi.mtg.deckbuilder.view.search.omnifilter;

	opens emi.mtg.deckbuilder.model to com.google.gson;
	opens emi.mtg.deckbuilder.view to com.google.gson, javafx.graphics, javafx.fxml;
	opens emi.mtg.deckbuilder.view.groupings to com.google.gson;
	opens emi.mtg.deckbuilder.view.sortings to com.google.gson;
	opens emi.mtg.deckbuilder.view.search to com.google.gson;
	opens emi.mtg.deckbuilder.view.dialogs to javafx.fxml;
	opens emi.mtg.deckbuilder.controller.serdes.impl to javafx.fxml;

	uses emi.lib.mtg.ImageSource;
	provides emi.lib.mtg.ImageSource with
			emi.mtg.deckbuilder.view.RenderedImageSource;

	uses emi.lib.mtg.DataSource;

	uses emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
	provides emi.mtg.deckbuilder.controller.serdes.DeckImportExport with
			emi.mtg.deckbuilder.controller.serdes.impl.Cockatrice,
			emi.mtg.deckbuilder.controller.serdes.impl.ImageExporter,
			emi.mtg.deckbuilder.controller.serdes.impl.MTGO,
			emi.mtg.deckbuilder.controller.serdes.impl.TextFile.Arena,
			emi.mtg.deckbuilder.controller.serdes.impl.TextFile.PlainText,
			emi.mtg.deckbuilder.controller.serdes.impl.SeparatedValues.CSV,
			emi.mtg.deckbuilder.controller.serdes.impl.SeparatedValues.TSV,
			emi.mtg.deckbuilder.controller.serdes.impl.SeparatedValues.BuylistTSV;

	uses Omnifilter.Subfilter;
	provides Omnifilter.Subfilter with
			emi.mtg.deckbuilder.view.search.omnifilter.filters.CardSet,
			emi.mtg.deckbuilder.view.search.omnifilter.filters.CardType,
			ColorFilter.ColorIdentity,
			ColorFilter.CardColor,
			emi.mtg.deckbuilder.view.search.omnifilter.filters.ManaValue,
			PowerToughnessLoyalty.Loyalty,
			PowerToughnessLoyalty.Power,
			PowerToughnessLoyalty.Toughness,
			emi.mtg.deckbuilder.view.search.omnifilter.filters.Rarity,
			emi.mtg.deckbuilder.view.search.omnifilter.filters.Regex,
			emi.mtg.deckbuilder.view.search.omnifilter.filters.RulesText;

	uses SearchProvider;
	provides SearchProvider with
			TextSearch,
			Omnifilter,
			ExpressionFilter;

	uses emi.mtg.deckbuilder.view.components.CardView.Sorting;
	provides emi.mtg.deckbuilder.view.components.CardView.Sorting with
			emi.mtg.deckbuilder.view.sortings.ManaValue,
			emi.mtg.deckbuilder.view.sortings.Color,
			emi.mtg.deckbuilder.view.sortings.ManaCost,
			emi.mtg.deckbuilder.view.sortings.Name,
			emi.mtg.deckbuilder.view.sortings.Rarity;

	uses emi.mtg.deckbuilder.view.components.CardView.Grouping;
	provides emi.mtg.deckbuilder.view.components.CardView.Grouping with
			emi.mtg.deckbuilder.view.groupings.CardTypeGroup,
			emi.mtg.deckbuilder.view.groupings.ColorGrouping,
			emi.mtg.deckbuilder.view.groupings.ManaValue,
			emi.mtg.deckbuilder.view.groupings.None,
			emi.mtg.deckbuilder.view.groupings.Rarity,
			emi.mtg.deckbuilder.view.groupings.TagGrouping;

	uses emi.mtg.deckbuilder.view.components.CardView.LayoutEngine.Factory;
	provides emi.mtg.deckbuilder.view.components.CardView.LayoutEngine.Factory with
			emi.mtg.deckbuilder.view.layouts.FlowGrid.Factory,
			emi.mtg.deckbuilder.view.layouts.Piles.Factory;
}
