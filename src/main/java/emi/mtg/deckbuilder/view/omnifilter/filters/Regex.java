package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="regex")
@Service.Property.String(name="shorthand", value="re")
public class Regex implements Omnifilter.FaceFilter {
	private Pattern pattern = null;

	public Regex(Context context, Omnifilter.Operator operator, String value) {
		try {
			if (operator == Omnifilter.Operator.DIRECT) {
				this.pattern = Pattern.compile(value);
			}
		} catch (PatternSyntaxException pse) {
			/* do nothing */
			pse.printStackTrace();
		}
	}

	@Override
	public boolean testFace(Card.Face face) {
		if (this.pattern != null) {
			return this.pattern.matcher(face.rules()).find();
		} else {
			return true;
		}
	}
}
