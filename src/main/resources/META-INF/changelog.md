## 2022

### Features

- Added Custom Text Format exporter. It doesn't support import (yet), but should let you export decks for unusual destinations like TCGPlayer (which expects set codes in square brackets).

### Improvements

- Saving a new deck or saving-as a deck now adds that deck to the "recent decks" list.

## January 2022

### Features

- Added the changelog, which you are hopefully now reading! Hi! I hope you're having a great day. :) Happy new year!
- Added preference to select preferred copy/paste format, though I do recommend leaving it on MtG:A, since that seems to have broad community support.
- Added preference to ignore various sets when selecting which printings to show/add by default.
- Expanded card tooltips to include tags (by default) and detailed card text (as a preference).

### Improvements

- Printing selector has an OK button and its auto mode works properly now.
- Separated memorized directories for saving/exporting and loading/importing.
- Added a comprehensible error message when loading a bad JSON file.
- Added "Export" option to deck tab context menu.

### Fixes

- Fixed saving and exporting UTF-8 collector numbers (e.g. JP Tamiyo, Collector of Tales).
- Fixed TSV buy - price column cell references. Still no pricing data, sorry.
- Color identity omnifilter once again correctly uses the entire card's color identity while filtering.
- Lands once again correctly include the color identity of their land types.
- Companions once again are validated correctly. Oops. Sorry about that!
- Reversible cards now properly show up as printings of the root card. Sorry, but you can't choose which side is shown.
- Outdated import/export plugins no longer prevent the deckbuilder from launching (I hope!).

### Changes

- Removed old 'prefer physical' and 'prefer standard' preferences, since they're superseded by set ignore/prefer options.

---

## November 2021

### Features

- The deckbuilder can now open multiple decks using tabs, in addition to as separate windows. You can drag and drop tabs between windows. or drag a tab onto itself to undock.
- The tabs show the deck's names. Double-click on a tab to change the deck name. Right click for a nifty context menu.
- Relatedly, added a preference to control windowing behavior when you open/create/import a deck: replace current, make a new tab, or make a new window. Asks by default.
- Up to five recently-opened decks are now remembered and available from Deck -> Open Recent...
- The deckbuilder now remembers the last directory where you opened, saved, imported, or exported a deck.
- Added a preference to prefer printings with earlier/later collector numbers (to try to avoid or prefer extended-border and showcase printings).
- Added a preference to prefer printings from sets which are "standard" (core, expansion, or precons) -- let me know if more precise control is wanted here...
- Added a preference to set the initial search query. The narrower the initial search, the quicker the collection loads!
- Modularized search providers. Choose your preferred search provider in Preferences. Defaults to Omnifilter.
- Added Simple Text filter which searches card names and rules text for the provided strings.
- Added Expression Filter, an extremely powerful search tool. Most useful when relating two characteristics of a card.
- Added Scryfall search, which farms out all the hard work to Scryfall. Requires internet. Limited to results with fewer than 1750 printings, since otherwise it takes an age.
- Omnifilter now supports combining logic like and/or/parentheses. (Only one layer of parentheses, though...)
- Added mana cost omnifilter (mana, manacost, mc; supports all comparisons)
- Added "is" omnifilter (is:commander, is:dfc, is:split, is:flip, is:permanent) -- let me know if there are other attributes you'd like!
- Added Arena-compatible import/export, as well as copy/paste actions.
- Added exporters for comma-separated values, tab-separated values, and a buylist TSV spreadsheet (handy for making it a paper deck).
- Added "save a copy" menu item to make a backup copy of a deck.

### Improvements

- The deck menu now has an option to disable deck auto-validation. If you're into that kinda thing.
- States of toggle buttons should be more obvious, esp. in dark mode/with screen modifiers like f.lux running.
- Stack traces during exceptions are a lot more readable now.
- Card scale sliders snap to 5% and show their scale factor beside them.
- Image exporter now only requests a width hint, and does so by number of cards wide. (Still inexact though.)
- The sideboard will be a bit smaller by default.
- Preferences are now split across a bunch of tabs. Hopefully they make some amount of sense.

### Fixes

- The deckbuilder will no longer crash if the update server is down or can't be reached.
- The option to choose a printing will no longer appear for cards with a single printing.
- Right clicking over no group will no longer produce an exception.
- Card type filters will no longer look for substrings of card types (e.g. "orc" matching "sorcery").
- If the future is now, upcoming supplementary set cards will also appear in the collection.
- If the future is now, all future-legal cards will be marked with an orange border.
- Hopefully fixed some partner/companion validation for Commander. Let me know if you see something weird.
- Pressing Alt no longer highlights all zone menus.
- Fixed previews of some split and flip cards dividing at the wrong position.

### Changes

- The window title will now be "<deck> - Deckbuilder" rather than the other way around. Easier alt-tabbing!
- Bonus-rarity cards from VMA now registers as "special" rather than "mythic rare".
- Tags and decktags are now unified. Tags assigned in the collection are carried into decks, but not the other way around.
- Pasting into the omnibar has been removed in favor of a global deck pasting function (for Arena mostly)
- Removed the ability to load decklists with variants. If you still have one, ping me -- I can fix them up manually.
- The deckbuilder mostly now uses set code + collector number to identify printings. This is used when saving decks or preferred printings. Please let me know if you experience errors related to either of these!

---
