## 2024 October

### Features

- Added a preference to control automatic data loading. Disable it to see your options for data sources. (There's probably only one.)
- Added plugin preferences. External plugins can now provide some simple controls over their behavior.
- The Scryfall plugin now provides an option to use MessagePack for serialization/deserialization, instead of JSON. MessagePack loses the benefit of JSON's human-readability, but saves a little hard drive space and seems to load decently faster. I'd recommend giving it a try!
- Added support for downloading a specific set of tags from Scryfall. Check the preferences dialog to choose which are included; the default is okay but not comprehensive.
  - Please note that downloading tags from Scryfall is a laborious process! It may take several minutes, and the more tags you want to download, the longer it will take.
- Added a preference to maximize the deckbuilder on startup.
- Copy-as-image (Shift+Shortcut+C) has been replaced with a copy format quick-picker, so you don't have to go into preferences and change your format.
- Paste-as-format (Shift+Shortcut+V) has been added, much like the above.
- You can now copy deck images as JPEGs, if you're tired of Discord complaining about your image upload sizes. (They're temporary files that are deleted when the program is closed.)
- Added "Copy Image" option to most card context menus, to quickly copy a card image to your clipboard.

### Improvements

- Hehe purple.
- The collection is now multithreaded! Modern processors should see a massive performance increase when searching.
- You can finally zoom in on cards by holding the Z key. You have no idea how much work this took in the background.
- Selection in piles views is more precise -- the selection has to overlap with the name line of the card to include it.
- Selection in all views is now unaffected by scrolling, so you can select whole swathes of cards if you really want to.
- Modified `~` and `CARDNAME` in regex and oracle searches to match cases where only a partial form of the card name is used (e.g. Derevi).
- Improved regex matching efficiency where `~` or `CARDNAME` is used.
- Search provider selection is now shown on the search bar itself (as long as it's wide enough). Easy access to Scryfall's `function:` tag search!
- The search bar now shows an indication when a search is ongoing.
- Card tooltips now contain the collector number of the card.
- Tooltips are universally a little larger/more readable.
- Added "Automatic (By Extension)" options to import and export dialogs to save you a bit of time.
- If you enable Auto mode on the collection and add a commander to an empty commander deck, it will go to the command zone instead of the library.
- Revamped the data update dialog/process to include support for other updateables (e.g. Scryfall tags).

### Fixes

- `Dragon's Approach` now correctly allows unlimited copies.
- Added `{L}` and `{D}` mana symbols and `Poly` card type added in MB2.
- Added `Kindred` card type replacing deprecated `Tribal` card type.
- Fixed `is:tdfc` returning no results.
- Fixed `is:mdfc` including some transforming DFCs and other stuff.
- Planes and Phenomenon cards are now rotated to be readable. (My neck is grateful.)
- You can once again drag cards onto the collection to just remove them from deck. Sorry!
- Fixed not being able to drag cards between groups in the collection pane.
- Brawl decks now correctly require exactly 60 cards, rather than exactly 100. (Look, I mostly play EDH...)
- Decks in the "recent decks" list that have been moved or deleted are now automatically removed.
- Unknown companion abilities will no longer cause uncaught exceptions. (They aren't verified though, sorry.)
- Freeform no longer performs any card legality validation. (So much changed under the hood, you have *no* idea.)
- Buylist TSV export now correctly references the "count" column rather than just putting the word "count" in there. (Google Sheets was *very* confused.)
- Fixed a possible cascade-failure when emergency saving a deck with invalid characters in its name.
- About dialog no longer tells you to contact me on Twitter. (I highly recommend leaving that place alone.)
- Fixed plain text and Arena importers not recognizing some set codes/collector numbers with dashes in them.
- Fixed a case where the collection would sometimes just... not load. (Due to a concurrent modification error while loading tags, of all things.)
- Improved logging the collection loading just in case another error causes it to fail. (Check the new debug console for details to send me if it does!)

### Changes

- `Printing` is now `Print` everywhere. ~~It's one syllable shorter; leave me alone.~~

---

## 2024 April

### Features

- Added validation of Doctor's Companion, Choose a Background, and Create a Character partner abilities.

### Improvements

- Added a "While Open" option to the cutboard behavior. If chosen, cards will be removed entirely while the cutboard is hidden, or sent to the cutboard while it's shown.
- Made the default JSON format more resilient against Scryfall changing collector numbers/set codes. If it can't find the exact card, it will try to use your preferred print of a card with that name.

### Fixes

- Fixed a bug where both faces of DFCs were using the same underlying card data. Hopefully I'm done changing that code now; it's a mess.
- Fixed the naming of DFC backface images. I recommend cleaning up your image cache after this change.

---

## 2023 September

### Fixes

- Fixed a number of DFCs not showing up due to a bug in the changes to the Scryfall plugin.

## 2023 August

### Features

- Added a toggle to hide or show the sideboard. Also added sideboards to Commander and Brawl decks.
- Added a "cutboard" for if you wanna hang onto cut cards to show what you thought about. Click the "CB" button to show or hide it. Enable the new "Remove Cards to Cutboard" preference to have double-clicked cards moved there first instead of removing them.

### Improvements

- Better backend handling of faces-as-printed. You won't notice a difference, probably. :)
- Added "Delete Saved Images" to cards in deck zones, and made it affect all prints of a card.
- When you add a non-empty deck to a window, if the window only contains an empty unmodified (e.g. new) deck, that tab will be closed.

### Fixes

- Dragging cards from one zone to a tag group in another will remove now old tags if you don't hold shift.

---

## 2023 July

### Improvements

- Battle is now a recognized type, and their front faces are rotated appropriately.
- New game formats on Scryfall shouldn't brick the deckbuilder. You won't be able to brew those formats with validation, though -- if we're missing a format you want to deckbuild for, shoot me a message!
- Drag and drop on Linux should be a lot less finicky. (Technically working around a JavaFX bug.)
- Zoom previews now dismiss mostly properly on Linux. (Another JavaFX workaround.)

### Fixes

- Fixed an issue preventing dragging cards directly into tagged groups of a deck.
- Saved sortings are correctly applied to the default views again.
- Updated to Scryfall plugin to support addition of "mutate" and "prototype" card frames.
- Updates should be correctly applied on Linux now. (Checked on an Ubuntu VM.)

---

## 2022

### Features

- Added Custom Text Format exporter. It doesn't support import (yet), but should let you export decks for unusual destinations like TCGPlayer (which expects set codes in square brackets).
- Added a context menu option to delete the saved/cached images of a card.
- Added undo/redo. It probably doesn't catch every deck change yet, though!

### Improvements

- Saving a new deck or saving-as a deck now adds that deck to the "recent decks" list.
- Added "is:dual", "is:triome", and "is:fetch" filters.
- Added power/toughness/loyalty and rarity/set to card tooltips.
- You can now drop a deck tab off the application to undock it.
- Import/export and load/save last-used paths are now separate. No more accidentally overwriting decklists with TTS objects!

### Changes

- Debug options are now hidden behind a preference toggle.
- Changed the default update URL to a personal domain name which won't lapse. Only fresh installs will catch this, though!

### Fixes

- Resolved download issues with Cloudflare intercepting requests to Scryfall's API. Hopefully -- I can't actually control this one.
- Windows now close when their last tab has been removed.
- Fixed some possible exceptions from dragging tabs around. There are possibly some I've missed though.
- Added Stickers and Universewalker card types to libmtg and fixed an issue with BFM's right half typeline.
- Changing a print now marks a deck as modified.

---

## January 2022

### Features

- Added the changelog, which you are hopefully now reading! Hi! I hope you're having a great day. :) Happy new year!
- Added preference to select preferred copy/paste format, though I do recommend leaving it on MtG:A, since that seems to have broad community support.
- Added preference to ignore various sets when selecting which prints to show/add by default.
- Expanded card tooltips to include tags (by default) and detailed card text (as a preference).

### Improvements

- Print selector has an OK button and its auto mode works properly now.
- Separated memorized directories for saving/exporting and loading/importing.
- Added a comprehensible error message when loading a bad JSON file.
- Added "Export" option to deck tab context menu.

### Fixes

- Fixed saving and exporting UTF-8 collector numbers (e.g. JP Tamiyo, Collector of Tales).
- Fixed TSV buy - price column cell references. Still no pricing data, sorry.
- Color identity omnifilter once again correctly uses the entire card's color identity while filtering.
- Lands once again correctly include the color identity of their land types.
- Companions once again are validated correctly. Oops. Sorry about that!
- Reversible cards now properly show up as prints of the root card. Sorry, but you can't choose which side is shown.
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
- Added a preference to prefer prints with earlier/later collector numbers (to try to avoid or prefer extended-border and showcase prints).
- Added a preference to prefer prints from sets which are "standard" (core, expansion, or precons) -- let me know if more precise control is wanted here...
- Added a preference to set the initial search query. The narrower the initial search, the quicker the collection loads!
- Modularized search providers. Choose your preferred search provider in Preferences. Defaults to Omnifilter.
- Added Simple Text filter which searches card names and rules text for the provided strings.
- Added Expression Filter, an extremely powerful search tool. Most useful when relating two characteristics of a card.
- Added Scryfall search, which farms out all the hard work to Scryfall. Requires internet. Limited to results with fewer than 1750 prints, since otherwise it takes an age.
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
- The option to choose a print will no longer appear for cards with a single print.
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
- The deckbuilder mostly now uses set code + collector number to identify prints. This is used when saving decks or preferred prints. Please let me know if you experience errors related to either of these!

---
