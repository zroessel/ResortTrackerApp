import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class App extends Application {

    // ── Design tokens ────────────────────────────────────────────────────────

    private static final String BG       = "#060d14";
    private static final String SURFACE  = "#0c1820";
    private static final String SURFACE2 = "#112030";
    private static final String SURFACE3 = "#162840";
    private static final String RIM      = "rgba(255,255,255,0.07)";  // used as hex below
    private static final String RIM_HEX  = "#111f2e";
    private static final String ICE      = "#a8d8ff";
    private static final String ICE_DIM  = "#4d8ab5";
    private static final String SNOW     = "#e8f4ff";
    private static final String MUTED    = "#2a4a60";
    private static final String SOFT     = "#4a6a82";
    private static final String GOLD     = "#f5c842";
    private static final String GREEN    = "#3dd68c";
    private static final String RED      = "#e8524a";

    // Serif display font (falls back gracefully)
    private static final String DISPLAY = "Georgia";
    private static final String BODY    = "System";

    // ── State ────────────────────────────────────────────────────────────────

    private final List<Resort> resorts = new ArrayList<>();

    private VBox  resortListBox;
    private VBox  spotlightBox;
    private FlowPane countryFlow;
    private final List<Button> countryButtons = new ArrayList<>();
    private Label resortsVisitedLabel, countriesVisitedLabel, continentsVisitedLabel;
    private Label resultSummaryLabel;
    private Button sortButton, clearFiltersButton;
    private ToggleButton visitedOnlyToggle;
    private TextField searchField;

    private BorderPane root;
    private VBox resortsPage, mapPage, profilePage;
    private Button tabResorts, tabMap, tabProfile;

    private String searchQuery    = "";
    private String selectedCountry = "All";
    private SortMode sortMode     = SortMode.A_TO_Z;

    // ── Application entry ────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        resorts.clear();
        resorts.addAll(loadResorts());

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");

        buildResortsPage();
        buildMapPage();
        buildProfilePage();

        root.setCenter(resortsPage);
        root.setBottom(buildTabBar());

        Scene scene = new Scene(root, 390, 720);
        stage.setTitle("Powder · Ski Tracker");
        stage.setScene(scene);
        stage.setMinWidth(360);
        stage.setMinHeight(600);
        stage.setResizable(false);
        stage.show();

        refreshView();
    }

    // ── Pages ────────────────────────────────────────────────────────────────

    private void buildResortsPage() {
        VBox content = new VBox(0);
        content.getChildren().addAll(
                buildPageHeader("My Resorts"),
                buildStatsStrip(),
                buildControls(),
                buildSpotlightSection(),
                buildResultsSection());

        ScrollPane scroll = styledScroll(content);
        resortsPage = wrapPage(scroll);
    }

    private void buildMapPage() {
        VBox content = new VBox(0,
                buildPageHeader("World Map"),
                buildContinentCard(),
                buildCountryCard());
        content.setStyle("-fx-background-color: " + BG + ";");

        mapPage = wrapPage(styledScroll(content));
    }

    private void buildProfilePage() {
        VBox content = new VBox(0,
                buildPageHeader("Profile"),
                buildProfileHero(),
                buildStatsCard(),
                buildTopCountriesCard());
        content.setStyle("-fx-background-color: " + BG + ";");

        profilePage = wrapPage(styledScroll(content));
    }

    // ── Tab bar ──────────────────────────────────────────────────────────────

    private HBox buildTabBar() {
        tabResorts = buildTabButton("Resorts", true);
        tabMap     = buildTabButton("Map", false);
        tabProfile = buildTabButton("Profile", false);

        tabResorts.setOnAction(e -> switchTab(0));
        tabMap    .setOnAction(e -> switchTab(1));
        tabProfile.setOnAction(e -> { buildProfilePage(); root.setCenter(profilePage); switchTab(2); });

        HBox bar = new HBox(0, tabResorts, tabMap, tabProfile);
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: " + BG + "; -fx-border-color: " + RIM_HEX + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        HBox.setHgrow(tabResorts, Priority.ALWAYS);
        HBox.setHgrow(tabMap,     Priority.ALWAYS);
        HBox.setHgrow(tabProfile, Priority.ALWAYS);
        return bar;
    }

    private Button buildTabButton(String label, boolean active) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(14, 0, 18, 0));
        btn.setStyle(tabStyle(active));
        return btn;
    }

    private void switchTab(int index) {
        if (index == 0) root.setCenter(resortsPage);
        else if (index == 1) root.setCenter(mapPage);
        else root.setCenter(profilePage);

        tabResorts.setStyle(tabStyle(index == 0));
        tabMap    .setStyle(tabStyle(index == 1));
        tabProfile.setStyle(tabStyle(index == 2));
    }

    // ── Resorts page components ──────────────────────────────────────────────

    private HBox buildPageHeader(String title) {
        // Split into plain + italic-ish second word via two labels
        String[] words = title.split(" ", 2);
        Label w1 = new Label(words[0] + (words.length > 1 ? " " : ""));
        w1.setTextFill(Color.web(SNOW));
        w1.setFont(Font.font(DISPLAY, FontWeight.NORMAL, 28));

        HBox header = new HBox(0, w1);
        if (words.length > 1) {
            Label w2 = new Label(words[1]);
            w2.setTextFill(Color.web(ICE));
            w2.setFont(Font.font(DISPLAY, FontWeight.NORMAL, 28));
            header.getChildren().add(w2);
        }
        header.setPadding(new Insets(18, 22, 10, 22));
        header.setAlignment(Pos.BOTTOM_LEFT);
        header.setStyle("-fx-background-color: " + BG + ";");
        return header;
    }

    private HBox buildStatsStrip() {
        resortsVisitedLabel    = statsNum("0");
        countriesVisitedLabel  = statsNum("0");
        continentsVisitedLabel = statsNum("0");

        HBox strip = new HBox(0,
                buildStatCell(resortsVisitedLabel, "Visited"),
                statDivider(),
                buildStatCell(countriesVisitedLabel, "Countries"),
                statDivider(),
                buildStatCell(continentsVisitedLabel, "Continents"));
        strip.setAlignment(Pos.CENTER);
        strip.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 14; -fx-border-color: " + RIM_HEX + "; -fx-border-radius: 14; -fx-border-width: 1;");
        strip.setPadding(new Insets(0));
        VBox wrapper = new VBox(strip);
        wrapper.setPadding(new Insets(0, 22, 16, 22));
        wrapper.setStyle("-fx-background-color: " + BG + ";");

        // Return as HBox trick — use wrapper cast via StackPane
        StackPane sp = new StackPane(wrapper);
        sp.setStyle("-fx-background-color: " + BG + ";");
        // Directly return strip wrapped in styled HBox
        HBox outer = new HBox(strip);
        outer.setPadding(new Insets(0, 22, 16, 22));
        outer.setStyle("-fx-background-color: " + BG + ";");
        HBox.setHgrow(strip, Priority.ALWAYS);
        return outer;
    }

    private Label statsNum(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web(SNOW));
        l.setFont(Font.font(DISPLAY, FontWeight.NORMAL, 26));
        l.setAlignment(Pos.CENTER);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private VBox buildStatCell(Label numLabel, String title) {
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.setTextFill(Color.web(SOFT));
        titleLabel.setFont(Font.font(BODY, FontWeight.BOLD, 10));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        VBox cell = new VBox(3, numLabel, titleLabel);
        cell.setAlignment(Pos.CENTER);
        cell.setPadding(new Insets(14, 8, 14, 8));
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private Region statDivider() {
        Region r = new Region();
        r.setMinWidth(1); r.setMaxWidth(1);
        r.setMinHeight(36);
        r.setStyle("-fx-background-color: " + RIM_HEX + ";");
        return r;
    }

    private VBox buildControls() {
        // Search
        searchField = new TextField();
        searchField.setPromptText("Search resorts, countries, regions…");
        searchField.setStyle(searchStyle());
        searchField.textProperty().addListener((obs, o, n) -> {
            searchQuery = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
            refreshView();
        });

        // Chips row
        visitedOnlyToggle = new ToggleButton("Visited");
        visitedOnlyToggle.setStyle(chipStyle(false));
        visitedOnlyToggle.setOnAction(e -> {
            visitedOnlyToggle.setStyle(chipStyle(visitedOnlyToggle.isSelected()));
            refreshView();
        });

        sortButton = new Button("A–Z");
        sortButton.setStyle(chipStyle(false));
        sortButton.setOnAction(e -> {
            sortMode = sortMode.next();
            sortButton.setText(sortMode.label);
            refreshView();
        });

        clearFiltersButton = new Button("✕ Clear");
        clearFiltersButton.setStyle(chipStyle(false));
        clearFiltersButton.setOnAction(e -> {
            searchField.clear();
            selectedCountry = "All";
            visitedOnlyToggle.setSelected(false);
            visitedOnlyToggle.setStyle(chipStyle(false));
            sortMode = SortMode.A_TO_Z;
            sortButton.setText("A–Z");
            refreshView();
        });

        HBox chipRow = new HBox(6, visitedOnlyToggle, sortButton, clearFiltersButton);
        chipRow.setPadding(new Insets(8, 22, 8, 22));

        // Country filters
        countryFlow = new FlowPane();
        countryFlow.setHgap(6); countryFlow.setVgap(6);
        countryFlow.setPadding(new Insets(0, 22, 14, 22));
        buildCountryButtons();

        VBox controls = new VBox(0,
                padded(searchField, new Insets(0, 22, 8, 22)),
                chipRow,
                countryFlow);
        controls.setStyle("-fx-background-color: " + BG + ";");
        return controls;
    }

    private VBox buildSpotlightSection() {
        spotlightBox = new VBox();
        spotlightBox.setStyle("-fx-background-color: " + BG + ";");
        return spotlightBox;
    }

    private VBox buildResultsSection() {
        resultSummaryLabel = new Label();
        resultSummaryLabel.setTextFill(Color.web(SOFT));
        resultSummaryLabel.setFont(Font.font(BODY, 11));

        Label title = new Label("All Resorts");
        title.setTextFill(Color.web(SNOW));
        title.setFont(Font.font(BODY, FontWeight.BOLD, 13));

        Label section = new Label("ALL RESORTS");
        section.setTextFill(Color.web(SOFT));
        section.setFont(Font.font(BODY, FontWeight.BOLD, 10));

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, section, spacer, resultSummaryLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 22, 6, 22));
        header.setStyle("-fx-background-color: " + BG + ";");

        resortListBox = new VBox(0);

        // Separator line above list
        Region sep = new Region();
        sep.setMinHeight(1); sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: " + RIM_HEX + ";");

        VBox section2 = new VBox(0, header, sep, resortListBox);
        section2.setStyle("-fx-background-color: " + BG + ";");
        return section2;
    }

    // ── Map page components ──────────────────────────────────────────────────

    private VBox buildContinentCard() {
        VBox card = sectionCard();
        card.getChildren().add(sectionCardHeader("By Continent"));

        String[] continents = {"North America", "Europe", "Asia", "Oceania", "Other"};
        for (int i = 0; i < continents.length; i++) {
            card.getChildren().add(buildContinentRow(continents[i], i == continents.length - 1));
        }
        return card;
    }

    private HBox buildContinentRow(String continent, boolean last) {
        long total   = resorts.stream().filter(r -> continentFor(r.country()).equals(continent)).count();
        long visited = resorts.stream().filter(r -> continentFor(r.country()).equals(continent) && r.visited()).count();
        double frac  = total == 0 ? 0 : (double) visited / total;

        Label name = new Label(continent);
        name.setTextFill(Color.web(SNOW));
        name.setFont(Font.font(BODY, 13));
        name.setMinWidth(110);

        Rectangle track = new Rectangle(0, 5);
        track.setArcWidth(5); track.setArcHeight(5);
        track.setFill(Color.web(SURFACE3));

        Rectangle fill = new Rectangle(0, 5);
        fill.setArcWidth(5); fill.setArcHeight(5);
        fill.setFill(Color.web(ICE));

        StackPane bar = new StackPane(track, fill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefWidth(120);
        bar.setMinWidth(80);
        HBox.setHgrow(bar, Priority.ALWAYS);

        // Bind widths after layout pass
        bar.widthProperty().addListener((obs, o, w) -> {
            track.setWidth(w.doubleValue());
            fill.setWidth(Math.max(0, w.doubleValue() * frac));
        });

        Label cnt = new Label(visited + "/" + total);
        cnt.setTextFill(Color.web(SOFT));
        cnt.setFont(Font.font(BODY, FontWeight.BOLD, 12));
        cnt.setMinWidth(36);
        cnt.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(10, name, bar, cnt);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        if (!last) row.setStyle("-fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    private VBox buildCountryCard() {
        VBox card = sectionCard();
        card.getChildren().add(sectionCardHeader("By Country"));

        List<String> countries = resorts.stream().map(Resort::country).distinct().sorted().collect(Collectors.toList());
        for (int i = 0; i < countries.size(); i++) {
            String c      = countries.get(i);
            long visited  = resorts.stream().filter(r -> r.country().equals(c) && r.visited()).count();
            long total    = resorts.stream().filter(r -> r.country().equals(c)).count();
            boolean last  = i == countries.size() - 1;

            Label nameL = new Label(c);
            nameL.setTextFill(Color.web(SNOW));
            nameL.setFont(Font.font(BODY, 13));

            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

            Label cntL = new Label(visited + " visited");
            cntL.setTextFill(Color.web(visited > 0 ? ICE : SOFT));
            cntL.setFont(Font.font(BODY, FontWeight.BOLD, 12));

            HBox row = new HBox(8, nameL, sp, cntL);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12, 16, 12, 16));
            if (!last) row.setStyle("-fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;");
            card.getChildren().add(row);
        }
        return card;
    }

    // ── Profile page components ──────────────────────────────────────────────

    private VBox buildProfileHero() {
        long visitedCount = resorts.stream().filter(Resort::visited).count();
        long total        = resorts.size();
        double pct        = total == 0 ? 0 : (double) visitedCount / total * 100;

        Label num = new Label(String.valueOf(visitedCount));
        num.setTextFill(Color.web(ICE));
        num.setFont(Font.font(DISPLAY, FontWeight.NORMAL, 72));
        num.setAlignment(Pos.CENTER);
        num.setMaxWidth(Double.MAX_VALUE);

        Label sub = new Label(String.format("of %d resorts visited · %.0f%%", total, pct));
        sub.setTextFill(Color.web(SOFT));
        sub.setFont(Font.font(BODY, 13));
        sub.setAlignment(Pos.CENTER);
        sub.setMaxWidth(Double.MAX_VALUE);

        Rectangle track = new Rectangle(0, 6);
        track.setArcWidth(6); track.setArcHeight(6);
        track.setFill(Color.web(SURFACE3));

        Rectangle fillBar = new Rectangle(0, 6);
        fillBar.setArcWidth(6); fillBar.setArcHeight(6);
        fillBar.setFill(Color.web(ICE_DIM));

        StackPane progBar = new StackPane(track, fillBar);
        progBar.setAlignment(Pos.CENTER_LEFT);
        progBar.widthProperty().addListener((obs, o, w) -> {
            track.setWidth(w.doubleValue());
            fillBar.setWidth(Math.max(6, w.doubleValue() * (pct / 100)));
        });

        VBox hero = new VBox(6, num, sub, progBar);
        hero.setPadding(new Insets(22, 16, 22, 16));
        hero.setAlignment(Pos.CENTER);
        hero.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 18; -fx-border-color: " + RIM_HEX + "; -fx-border-radius: 18; -fx-border-width: 1;");

        VBox wrapper = new VBox(hero);
        wrapper.setPadding(new Insets(0, 22, 14, 22));
        wrapper.setStyle("-fx-background-color: " + BG + ";");
        return wrapper;
    }

    private VBox buildStatsCard() {
        Set<String> visitedCountries = resorts.stream().filter(Resort::visited).map(Resort::country)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> visitedContinents = resorts.stream().filter(Resort::visited).map(r -> continentFor(r.country()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long wishlist = resorts.stream().filter(r -> !r.visited()).count();

        VBox card = sectionCard();
        card.getChildren().addAll(
                sectionCardHeader("Stats"),
                statCardRow("Countries skied",    String.valueOf(visitedCountries.size()), ICE, false),
                statCardRow("Continents reached", String.valueOf(visitedContinents.size()), ICE, true),
                statCardRow("On wishlist",         String.valueOf(wishlist), SOFT, false),
                statCardRow("Total in catalog",    String.valueOf(resorts.size()), SOFT, true));
        return card;
    }

    private VBox buildTopCountriesCard() {
        VBox card = sectionCard();
        card.getChildren().add(sectionCardHeader("Most Visited Countries"));

        List<Map.Entry<String, Long>> top = resorts.stream()
                .filter(Resort::visited)
                .collect(Collectors.groupingBy(Resort::country, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        for (int i = 0; i < top.size(); i++) {
            var e = top.get(i);
            card.getChildren().add(statCardRow(e.getKey(), e.getValue() + " visited", ICE, i == top.size() - 1));
        }
        return card;
    }

    private HBox statCardRow(String label, String value, String valueColor, boolean last) {
        Label nameL = new Label(label);
        nameL.setTextFill(Color.web(SNOW));
        nameL.setFont(Font.font(BODY, 13));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label valL = new Label(value);
        valL.setTextFill(Color.web(valueColor));
        valL.setFont(Font.font(BODY, FontWeight.BOLD, 13));

        HBox row = new HBox(8, nameL, sp, valL);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        if (!last) row.setStyle("-fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    private void refreshView() {
        updateStats();
        updateSpotlight();
        updateCountryButtonStyles();

        List<Resort> filtered = filteredResorts();
        resultSummaryLabel.setText(filtered.size() + " shown");
        clearFiltersButton.setDisable(
                searchQuery.isBlank() && selectedCountry.equals("All")
                && !visitedOnlyToggle.isSelected() && sortMode == SortMode.A_TO_Z);

        resortListBox.getChildren().clear();
        if (filtered.isEmpty()) {
            resortListBox.getChildren().add(emptyState());
            return;
        }
        for (int i = 0; i < filtered.size(); i++) {
            resortListBox.getChildren().add(createResortRow(filtered.get(i)));
        }
    }

    private void updateStats() {
        long v = resorts.stream().filter(Resort::visited).count();
        long c = resorts.stream().filter(Resort::visited).map(Resort::country).distinct().count();
        long ct = resorts.stream().filter(Resort::visited).map(r -> continentFor(r.country())).distinct().count();

        resortsVisitedLabel   .setText(String.valueOf(v));
        countriesVisitedLabel .setText(String.valueOf(c));
        continentsVisitedLabel.setText(String.valueOf(ct));
    }

    private void updateSpotlight() {
        spotlightBox.getChildren().clear();

        Resort next = resorts.stream().filter(r -> !r.visited()).findFirst().orElse(null);
        if (next == null) return;

        Label eyebrow = new Label("⬡  NEXT ON LIST");
        eyebrow.setTextFill(Color.web(GOLD));
        eyebrow.setFont(Font.font(BODY, FontWeight.BOLD, 10));

        Label name = new Label(next.name());
        name.setTextFill(Color.web(SNOW));
        name.setFont(Font.font(DISPLAY, FontWeight.NORMAL, 20));

        Label loc = new Label(next.region() + "  ·  " + next.country());
        loc.setTextFill(Color.web(SOFT));
        loc.setFont(Font.font(BODY, 12));

        Button markBtn = new Button("Mark visited");
        markBtn.setStyle(primaryBtnStyle());
        markBtn.setOnAction(e -> { next.setVisited(true); saveResorts(); refreshView(); });

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bottom = new HBox(sp, markBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(5, eyebrow, name, loc, bottom);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle("-fx-background-color: " + SURFACE2 + "; -fx-background-radius: 16; -fx-border-color: " + RIM_HEX + "; -fx-border-radius: 16; -fx-border-width: 1;");

        VBox wrapper = new VBox(card);
        wrapper.setPadding(new Insets(0, 22, 14, 22));
        wrapper.setStyle("-fx-background-color: " + BG + ";");
        spotlightBox.getChildren().add(wrapper);
    }

    private VBox emptyState() {
        Label l = new Label("No resorts match these filters.");
        l.setTextFill(Color.web(SOFT));
        l.setFont(Font.font(BODY, 14));
        VBox box = new VBox(l);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 22, 40, 22));
        return box;
    }

    private HBox createResortRow(Resort resort) {
        Circle dot = new Circle(4, resort.visited() ? Color.web(GREEN) : Color.TRANSPARENT);
        dot.setStroke(resort.visited() ? Color.web(GREEN) : Color.web(MUTED));
        dot.setStrokeWidth(1.5);

        Label name = new Label(resort.name());
        name.setTextFill(Color.web(SNOW));
        name.setFont(Font.font(BODY, FontWeight.BOLD, 14));

        Label loc = new Label(resort.country() + "  ·  " + resort.region());
        loc.setTextFill(Color.web(MUTED));
        loc.setFont(Font.font(BODY, 12));

        VBox text = new VBox(2, name, loc);
        HBox.setHgrow(text, Priority.ALWAYS);

        // Circle action button
        Label actionLabel = new Label(resort.visited() ? "✓" : "+");
        actionLabel.setFont(Font.font(BODY, FontWeight.BOLD, 14));
        actionLabel.setAlignment(Pos.CENTER);

        StackPane action = new StackPane(actionLabel);
        action.setMinSize(30, 30);
        action.setMaxSize(30, 30);
        action.setStyle(resort.visited() ? visitedActionStyle() : addActionStyle());
        action.setOnMouseClicked(e -> { resort.setVisited(!resort.visited()); saveResorts(); refreshView(); });
        action.setStyle(resort.visited() ? visitedActionStyle() : addActionStyle());

        HBox row = new HBox(12, dot, text, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(13, 22, 13, 22));
        row.setStyle("-fx-background-color: " + BG + "; -fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;");

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color: " + BG + "; -fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;"));

        FadeTransition ft = new FadeTransition(Duration.millis(100), row);
        ft.setFromValue(0.6); ft.setToValue(1.0); ft.play();

        return row;
    }

    // ── Country filter buttons ────────────────────────────────────────────────

    private void buildCountryButtons() {
        countryFlow.getChildren().clear();
        countryButtons.clear();
        List<String> countries = new ArrayList<>(List.of("All"));
        countries.addAll(resorts.stream().map(Resort::country).distinct().sorted().collect(Collectors.toList()));
        for (String c : countries) {
            Button btn = new Button(c);
            btn.setStyle(chipStyle(c.equals(selectedCountry)));
            btn.setOnAction(e -> { selectedCountry = c; refreshView(); });
            countryButtons.add(btn);
            countryFlow.getChildren().add(btn);
        }
    }

    private void updateCountryButtonStyles() {
        for (Button b : countryButtons) b.setStyle(chipStyle(b.getText().equals(selectedCountry)));
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    private List<Resort> filteredResorts() {
        Comparator<Resort> cmp = switch (sortMode) {
            case A_TO_Z        -> Comparator.comparing(Resort::name);
            case Z_TO_A        -> Comparator.comparing(Resort::name).reversed();
            case VISITED_FIRST -> Comparator.comparing(Resort::visited).reversed().thenComparing(Resort::name);
        };
        return resorts.stream()
                .filter(r -> selectedCountry.equals("All") || r.country().equals(selectedCountry))
                .filter(r -> !visitedOnlyToggle.isSelected() || r.visited())
                .filter(r -> matchesSearch(r, searchQuery))
                .sorted(cmp)
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(Resort r, String q) {
        if (q.isBlank()) return true;
        return (r.name() + " " + r.country() + " " + r.region() + " " + r.description())
                .toLowerCase(Locale.ROOT).contains(q);
    }

    private List<Resort> loadResorts() {
        Path file = resolveResortPath();
        List<Resort> loaded = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", 5);
                if (p.length < 5) continue;
                loaded.add(new Resort(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(), Boolean.parseBoolean(p[4].trim())));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load resorts from " + file, ex);
        }
        return loaded;
    }

    private void saveResorts() {
        Path file = resolveResortPath();
        List<String> lines = resorts.stream()
                .map(r -> String.join(",", r.name(), r.country(), r.region(), r.description(), String.valueOf(r.visited())))
                .collect(Collectors.toList());
        try { Files.write(file, lines, StandardCharsets.UTF_8); }
        catch (IOException ex) { throw new IllegalStateException("Could not save resorts", ex); }
    }

    private Path resolveResortPath() {
        for (Path p : new Path[]{Paths.get("resorts.txt"), Paths.get("..", "resorts.txt"), Paths.get("C:\\SkiTrackerApp\\resorts.txt")}) {
            Path n = p.toAbsolutePath().normalize();
            if (Files.exists(n)) return n;
        }
        throw new IllegalStateException("Could not find resorts.txt");
    }

    private String continentFor(String country) {
        return switch (country) {
            case "Canada", "USA"                              -> "North America";
            case "France", "Switzerland", "Austria", "Italy",
                 "Germany", "Sweden", "Norway", "Finland",
                 "Bulgaria", "Slovakia", "Poland", "Spain"   -> "Europe";
            case "Japan"                                      -> "Asia";
            case "Australia", "New Zealand"                  -> "Oceania";
            default                                          -> "Other";
        };
    }

    // ── Layout helpers ───────────────────────────────────────────────────────

    private ScrollPane styledScroll(VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + "; -fx-border-color: transparent;");
        return sp;
    }

    private VBox wrapPage(ScrollPane sp) {
        VBox page = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        page.setStyle("-fx-background-color: " + BG + ";");
        return page;
    }

    private VBox sectionCard() {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 14; -fx-border-color: " + RIM_HEX + "; -fx-border-radius: 14; -fx-border-width: 1;");
        VBox wrapper = new VBox(card);
        wrapper.setPadding(new Insets(0, 22, 14, 22));
        wrapper.setStyle("-fx-background-color: " + BG + ";");
        // Return the wrapper so callers add to wrapper.getChildren() indirectly via card — but we need card back
        // Use tag trick: store card in wrapper's user data
        wrapper.setUserData(card);
        return wrapper;
    }

    // Override to return the inner card from the wrapper
    // Actually simplify: return a VBox that IS the card, styled with padding wrapper
    private VBox cardSection(String header) {
        VBox inner = new VBox(0);
        inner.getChildren().add(sectionCardHeader(header));
        inner.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 14; -fx-border-color: " + RIM_HEX + "; -fx-border-radius: 14; -fx-border-width: 1;");

        VBox wrapper = new VBox(inner);
        wrapper.setPadding(new Insets(0, 22, 14, 22));
        wrapper.setStyle("-fx-background-color: " + BG + ";");
        wrapper.setUserData(inner);
        return wrapper;
    }

    private Label sectionCardHeader(String text) {
        Label l = new Label(text.toUpperCase());
        l.setTextFill(Color.web(SOFT));
        l.setFont(Font.font(BODY, FontWeight.BOLD, 10));
        l.setPadding(new Insets(12, 16, 10, 16));
        l.setStyle("-fx-border-color: transparent transparent " + RIM_HEX + " transparent; -fx-border-width: 0 0 1 0;");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private VBox padded(javafx.scene.Node node, Insets insets) {
        VBox b = new VBox(node);
        b.setPadding(insets);
        b.setStyle("-fx-background-color: " + BG + ";");
        return b;
    }

    // ── Styles ───────────────────────────────────────────────────────────────

    private String searchStyle() {
        return "-fx-background-color: " + SURFACE + ";" +
               "-fx-text-fill: " + SNOW + ";" +
               "-fx-prompt-text-fill: " + SOFT + ";" +
               "-fx-background-radius: 12;" +
               "-fx-border-color: " + RIM_HEX + ";" +
               "-fx-border-radius: 12;" +
               "-fx-border-width: 1;" +
               "-fx-padding: 11 14 11 14;" +
               "-fx-font-size: 14;" +
               "-fx-font-family: '" + BODY + "';";
    }

    private String chipStyle(boolean active) {
        return "-fx-background-color: " + (active ? ICE_DIM : SURFACE) + ";" +
               "-fx-text-fill: " + (active ? SNOW : SOFT) + ";" +
               "-fx-background-radius: 20;" +
               "-fx-border-color: " + (active ? ICE : RIM_HEX) + ";" +
               "-fx-border-radius: 20;" +
               "-fx-border-width: 1;" +
               "-fx-padding: 5 12 5 12;" +
               "-fx-cursor: hand;" +
               "-fx-font-size: 12;" +
               "-fx-font-weight: bold;" +
               "-fx-font-family: '" + BODY + "';";
    }

    private String primaryBtnStyle() {
        return "-fx-background-color: " + ICE + ";" +
               "-fx-text-fill: #060d14;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 10;" +
               "-fx-border-width: 0;" +
               "-fx-padding: 8 16 8 16;" +
               "-fx-cursor: hand;" +
               "-fx-font-size: 13;" +
               "-fx-font-family: '" + BODY + "';";
    }

    private String visitedActionStyle() {
        return "-fx-background-color: rgba(61,214,140,0.1);" +
               "-fx-background-radius: 15;" +
               "-fx-border-color: " + GREEN + ";" +
               "-fx-border-radius: 15;" +
               "-fx-border-width: 1.5;" +
               "-fx-text-fill: " + GREEN + ";" +
               "-fx-cursor: hand;";
    }

    private String addActionStyle() {
        return "-fx-background-color: transparent;" +
               "-fx-background-radius: 15;" +
               "-fx-border-color: " + MUTED + ";" +
               "-fx-border-radius: 15;" +
               "-fx-border-width: 1.5;" +
               "-fx-text-fill: " + SOFT + ";" +
               "-fx-cursor: hand;";
    }

    private String tabStyle(boolean active) {
        return "-fx-background-color: transparent;" +
               "-fx-text-fill: " + (active ? ICE : SOFT) + ";" +
               "-fx-font-size: 11;" +
               "-fx-font-weight: " + (active ? "bold" : "normal") + ";" +
               "-fx-border-color: transparent;" +
               "-fx-cursor: hand;" +
               "-fx-font-family: '" + BODY + "';";
    }

    // ── Main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) { launch(args); }

    // ── Enums & inner classes ────────────────────────────────────────────────

    private enum SortMode {
        A_TO_Z("A–Z"), Z_TO_A("Z–A"), VISITED_FIRST("Visited");
        final String label;
        SortMode(String l) { this.label = l; }
        SortMode next() { return switch (this) { case A_TO_Z -> Z_TO_A; case Z_TO_A -> VISITED_FIRST; case VISITED_FIRST -> A_TO_Z; }; }
    }

    private static class Resort {
        private final String name, country, region, description;
        private boolean visited;
        Resort(String name, String country, String region, String description, boolean visited) {
            this.name = name; this.country = country; this.region = region;
            this.description = description; this.visited = visited;
        }
        String name()        { return name; }
        String country()     { return country; }
        String region()      { return region; }
        String description() { return description; }
        boolean visited()    { return visited; }
        void setVisited(boolean v) { this.visited = v; }
    }
}