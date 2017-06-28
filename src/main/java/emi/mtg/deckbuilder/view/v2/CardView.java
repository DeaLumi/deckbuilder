package emi.mtg.deckbuilder.view.v2;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Predicate;

public class CardView extends Canvas implements ListChangeListener<CardInstance> {
    // TODO: Turn these into properties that can change? This renderer is FAST!
    public static final double WIDTH = 200.0;
    public static final double HEIGHT = 280.0;
    public static final double PADDING = WIDTH / 20.0;

    public static class MVec2d implements Comparable<MVec2d> {
        public double x, y;

        public MVec2d(double x, double y) {
            set(x, y);
        }

        public MVec2d(MVec2d other) {
            this(other.x, other.y);
        }

        public MVec2d() {
            this(0.0, 0.0);
        }

        public MVec2d set(double x, double y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public MVec2d set(MVec2d other) {
            return set(other.x, other.y);
        }

        public MVec2d plus(double x, double y) {
            this.x += x;
            this.y += y;
            return this;
        }

        public MVec2d plus(MVec2d other) {
            return plus(other.x, other.y);
        }

        public MVec2d negate() {
            this.x = -this.x;
            this.y = -this.y;
            return this;
        }

        @Override
        public int compareTo(MVec2d o) {
            double dy = this.y - o.y;

            if (dy != 0) {
                return (int) Math.signum(dy);
            }

            return (int) Math.signum(this.x - o.x);
        }

        @Override
        public int hashCode() {
            short hi = (short) (Double.doubleToRawLongBits(x) & 0xFFFF);
            short lo = (short) (Double.doubleToRawLongBits(y) & 0xFFFF);
            return (hi << 16) | lo;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MVec2d)) {
                return false;
            }

            return x == ((MVec2d) obj).x && y == ((MVec2d) obj).y;
        }
    }

    public static class Bounds {
        public MVec2d pos, dim;

        public Bounds() {
            this.pos = new MVec2d();
            this.dim = new MVec2d();
        }
    }

    public static class Indices {
        public int group, card;

        public Indices(int group, int card) {
            set(group, card);
        }

        public Indices() {
            this(0, 0);
        }

        public Indices set(int group, int card) {
            this.group = group;
            this.card = card;
            return this;
        }

        public Indices set(Indices other) {
            return set(other.group, other.card);
        }
    }

    @Service(CardView.class)
    @Service.Property.String(name="name")
    public interface LayoutEngine {
        default boolean layoutChanged() {
            return false;
        }

        Bounds[] layoutGroups(int[] groupSizes);

        MVec2d coordinatesOf(Indices indices, MVec2d buffer);
        Indices indicesAt(MVec2d point, Indices buffer);
    }

    private static final Map<String, Service.Loader<LayoutEngine>.Stub> engineMap = new HashMap<>();

    static {
        Service.Loader<LayoutEngine> loader = Service.Loader.load(LayoutEngine.class);

        for (Service.Loader<LayoutEngine>.Stub stub : loader) {
            engineMap.put(stub.string("name"), stub);
        }
    }

    public static Set<String> engineNames() {
        return CardView.engineMap.keySet();
    }

    private static final Map<Card, Image> imageCache = new HashMap<>();
    private static final Image CARD_BACK = new Image("file:Back.xlhq.jpg", WIDTH, HEIGHT, true, true);

    private final ImageSource images;

    private final ObservableList<CardInstance> model;
    private final FilteredList<CardInstance> filteredModel;
    private final SortedList<CardInstance> sortedModel;

    private LayoutEngine engine;
    private Predicate<CardInstance> filter;
    private Comparator<CardInstance> sort;
    private String[] groups;
    private Function<CardInstance, String> groupExtractor;

    private final Map<String, Integer> groupIndexMap;

    private double scrollX, scrollY;

    public CardView(ImageSource images, ObservableList<CardInstance> model) {
        super(1024, 1024);

        this.images = images;
        this.model = model;
        this.filteredModel = model.filtered(ci -> true);
        this.sortedModel = this.filteredModel.sorted(CardPane.NAME_SORT);

        this.groupIndexMap = new HashMap<>();
        setOnScroll(se -> {
            scrollX += se.getDeltaX();
            scrollY += se.getDeltaY();
            scheduleRender();
        });
    }

    public void layout(String engine) {
        if (CardView.engineMap.containsKey(engine)) {
            this.engine = CardView.engineMap.get(engine).uncheckedInstance(this);
            scheduleRender();
        }
    }

    public void filter(Predicate<CardInstance> filter) {
        this.filter = filter;
        this.filteredModel.setPredicate(filter);
    }

    public void sort(Comparator<CardInstance> sort) {
        this.sort = sort;
        this.sortedModel.setComparator(sort);
    }

    public void group(String[] groups, Function<CardInstance, String> groupExtractor) {
        this.groups = groups;

        this.groupIndexMap.clear();
        for (int i = 0; i < groups.length; ++i) {
            this.groupIndexMap.put(groups[i], i);
        }

        this.groupExtractor = groupExtractor;
        scheduleRender();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double minWidth(double height) {
        return 0.0;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double maxWidth(double height) {
        return 8192.0;
    }

    @Override
    public double minHeight(double width) {
        return 0.0;
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    @Override
    public double maxHeight(double width) {
        return 8192.0;
    }

    @Override
    public void resize(double width, double height) {
        setWidth(width);
        setHeight(height);
        scheduleRender();
    }

    @Override
    public void onChanged(Change<? extends CardInstance> c) {
        while (c.next()) {
            if (!c.wasAdded()) {
                continue;
            }

            // TODO: Background load here
            for (CardInstance ci : c.getAddedSubList()) {
                URL url = this.images.find(ci.card());

                if (url != null) {
                    imageCache.put(ci.card(), new Image(url.toString(), WIDTH, HEIGHT, true, true));
                }
            }
        }

        scheduleRender();
    }

    public void scheduleRender() {
        ForkJoinPool.commonPool().submit(this::render);
    }

    class CardList extends ArrayList<CardInstance> {

    }

    protected void render() {
        if (engine == null) {
            return;
        }

        if (sortedModel.size() > 200) {
            Platform.runLater(() -> {
                GraphicsContext gfx = getGraphicsContext2D();
                gfx.clearRect(0, 0, getWidth(), getHeight());
                gfx.setTextAlign(TextAlignment.CENTER);
                gfx.setFill(Color.BLACK);
                gfx.setFont(new Font(null, getHeight() / 10.0));
                gfx.fillText("Too many cards to display.", getWidth() / 2, getHeight() / 2);
            });
            return;
        }

        int[] groupSizes = new int[groups.length];
        CardList[] cardLists = new CardList[groups.length];

        // One complete pass through the list... TODO: use streams?
        for (CardInstance ci : sortedModel) {
            final int i = groupIndexMap.get(groupExtractor.apply(ci));
            if (cardLists[i] == null) {
                cardLists[i] = new CardList();
            }

            cardLists[i].add(ci);
            ++groupSizes[i];
        }

        Bounds[] groupBounds = engine.layoutGroups(groupSizes);

        MVec2d low = new MVec2d(), high = new MVec2d();

        for (int i = 0; i < groupBounds.length; ++i) {
            final Bounds bounds = groupBounds[i];

            if (bounds.pos.x < low.x) {
                low.x = bounds.pos.x;
            }

            if (bounds.pos.y < low.y) {
                low.y = bounds.pos.y;
            }

            if (bounds.pos.x + bounds.dim.x > high.x) {
                high.x = bounds.pos.x + bounds.dim.x;
            }

            if (bounds.pos.y + bounds.dim.y > high.y) {
                high.y = bounds.pos.y + bounds.dim.y;
            }
        }

        // TODO: Create min/max scroll X/Y properties and draw scroll bars at edges.
        scrollX = -Math.min(high.x - getWidth(), Math.max(-scrollX, low.x));
        scrollY = -Math.min(high.y - getHeight(), Math.max(-scrollY, low.y));

        SortedMap<MVec2d, Image> renderMap = new TreeMap<>();
        Indices ind = new Indices();
        MVec2d loc = new MVec2d();

        for (int i = 0; i < groups.length; ++i) {
            if (cardLists[i] == null) {
                continue;
            }

            final Bounds bounds = groupBounds[i];
            bounds.pos.plus(scrollX, scrollY);

            if (bounds.pos.x < -bounds.dim.x || bounds.pos.x > getWidth() || bounds.pos.y < -bounds.dim.y || bounds.pos.y > getHeight()) {
                continue;
            }

            ind.group = i;
            for (int j = 0; j < cardLists[i].size(); ++j) {
                ind.card = j;

                loc = engine.coordinatesOf(ind, loc);
                loc = loc.plus(scrollX, scrollY);

                if (loc.x < -WIDTH || loc.x > getWidth() || loc.y < -HEIGHT || loc.y > getHeight()) {
                    continue;
                }

                final Card card = cardLists[i].get(j).card();
                if (CardView.imageCache.containsKey(card)) {
                    renderMap.put(new MVec2d(loc), CardView.imageCache.get(card));
                } else {
                    renderMap.put(new MVec2d(loc), CARD_BACK);

                    ForkJoinPool.commonPool().submit(() -> {
                        URL url = this.images.find(card);

                        if (url != null) {
                            CardView.imageCache.put(card, new Image(url.toString(), WIDTH, HEIGHT, true, true, false));
                            scheduleRender();
                        } else {
                            System.err.println("Unable to load image for " + card.set().code() + "/" + card.name());
                            CardView.imageCache.put(card, CARD_BACK);
                        }
                    });
                }
            }
        }

        Platform.runLater(() -> {
            GraphicsContext gfx = getGraphicsContext2D();
            gfx.clearRect(0, 0, getWidth(), getHeight());
            for (Map.Entry<MVec2d, Image> img : renderMap.entrySet()) {
                gfx.drawImage(img.getValue(), img.getKey().x, img.getKey().y, WIDTH, HEIGHT);
            }
        });
    }
}
