package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Font, size, line spacing, margins, justification and hyphenation, the colours of the page and one or two
 * pages. Every change applies at once.
 */
final class AppearancePanel extends JPanel {
    /** Book fonts that read well, offered first when they are installed. */
    static final List<String> PREFERRED = List.of("DejaVu Serif", "DejaVu Sans", "Liberation Serif", "Liberation Sans",
            "Noto Serif", "Noto Sans", "Gentium Plus", "Linux Libertine O", "TeX Gyre Pagella", "Charis SIL");

    private final Messages msg;
    private final JComboBox<String> family = new JComboBox<>();
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(18, 8, 48, 1));
    private final JSlider spacing = new JSlider(100, 250, 140);
    private final JSlider margin = new JSlider(0, 25, 8);
    private final JCheckBox justify;
    private final JCheckBox hyphenate;
    private final Map<ReadTheme, JToggleButton> themes = new EnumMap<>(ReadTheme.class);
    private final JRadioButton single;
    private final JRadioButton two;
    private Consumer<Appearance> changed = a -> { };
    private Consumer<ReadTheme> themeChosen = t -> { };
    private Consumer<Boolean> pagesChosen = b -> { };
    private Appearance appearance = Appearance.DEFAULT;
    private boolean hasFonts;
    private boolean updating;

    AppearancePanel(Messages msg) {
        super(new GridBagLayout());
        this.msg = msg;
        setName("appearance");
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        family.setName("appearance.font");
        family.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                String text = Appearance.PUBLISHER.equals(value) ? msg.get("appearance.font.publisher") : String.valueOf(value);
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        family.setMaximumRowCount(16);
        size.setName("appearance.size");
        spacing.setName("appearance.spacing");
        margin.setName("appearance.margin");
        justify = Mnemonics.apply(new JCheckBox(), msg.get("appearance.justify"));
        justify.setName("appearance.justify");
        hyphenate = Mnemonics.apply(new JCheckBox(), msg.get("appearance.hyphenate"));
        hyphenate.setName("appearance.hyphenate");

        JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup themeGroup = new ButtonGroup();
        for (ReadTheme t : ReadTheme.values()) {
            JToggleButton b = new JToggleButton(msg.get("theme." + t.key()), AmadeUI.colorDot(t.background(), 14));
            b.setName("theme." + t.key());
            b.addActionListener(e -> {
                if (!updating) themeChosen.accept(t);
            });
            themeGroup.add(b);
            themes.put(t, b);
            themeRow.add(b);
        }
        JPanel pagesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup pagesGroup = new ButtonGroup();
        single = Mnemonics.apply(new JRadioButton(), msg.get("appearance.pages.one"));
        single.setName("appearance.pages.one");
        two = Mnemonics.apply(new JRadioButton(), msg.get("appearance.pages.two"));
        two.setName("appearance.pages.two");
        for (JRadioButton b : List.of(single, two)) {
            pagesGroup.add(b);
            pagesRow.add(b);
            b.addActionListener(e -> {
                if (!updating) pagesChosen.accept(two.isSelected());
            });
        }

        int y = 0;
        y = row(y, "appearance.font", family);
        y = row(y, "appearance.size", size);
        y = row(y, "appearance.spacing", spacing);
        y = row(y, "appearance.margin", margin);
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checks.add(justify);
        checks.add(hyphenate);
        y = row(y, null, checks);
        y = row(y, "appearance.theme", themeRow);
        row(y, "appearance.pages", pagesRow);

        family.addActionListener(e -> fire());
        size.addChangeListener(e -> fire());
        spacing.addChangeListener(e -> {
            if (!spacing.getValueIsAdjusting()) fire();
        });
        margin.addChangeListener(e -> {
            if (!margin.getValueIsAdjusting()) fire();
        });
        justify.addActionListener(e -> fire());
        hyphenate.addActionListener(e -> fire());
        fillFamilies(false);
    }

    private int row(int y, String key, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        if (key != null) {
            JLabel label = new JLabel(msg.get(key));
            c.gridx = 0;
            add(label, c);
        }
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(field, c);
        return y + 1;
    }

    /** The publisher's fonts when the book has some, the preferred fonts that are installed, then all others. */
    private void fillFamilies(boolean publisher) {
        Set<String> installed = new LinkedHashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()));
        List<String> names = new ArrayList<>();
        if (publisher) names.add(Appearance.PUBLISHER);
        for (String f : PREFERRED) if (installed.contains(f)) names.add(f);
        for (String f : installed) if (!names.contains(f) && !f.startsWith(".")) names.add(f);
        updating = true;
        try {
            family.removeAllItems();
            for (String n : names) family.addItem(n);
        } finally {
            updating = false;
        }
    }

    void onChange(Consumer<Appearance> action) {
        changed = action;
    }

    void onTheme(Consumer<ReadTheme> action) {
        themeChosen = action;
    }

    void onPages(Consumer<Boolean> action) {
        pagesChosen = action;
    }

    void show(Appearance a, boolean bookHasFonts) {
        if (bookHasFonts != hasFonts) {
            hasFonts = bookHasFonts;
            fillFamilies(bookHasFonts);
        }
        appearance = a;
        updating = true;
        try {
            if (((javax.swing.DefaultComboBoxModel<String>) family.getModel()).getIndexOf(a.family()) < 0) {
                family.addItem(a.family());
            }
            family.setSelectedItem(a.family());
            size.setValue(a.size());
            spacing.setValue((int) Math.round(a.lineSpacing() * 100));
            margin.setValue((int) Math.round(a.margin() * 100));
            justify.setSelected(a.justify());
            hyphenate.setSelected(a.hyphenate());
        } finally {
            updating = false;
        }
    }

    void showTheme(ReadTheme theme, boolean twoPages) {
        updating = true;
        try {
            themes.get(theme).setSelected(true);
            (twoPages ? two : single).setSelected(true);
        } finally {
            updating = false;
        }
    }

    private void fire() {
        if (updating || family.getSelectedItem() == null) return;
        Appearance a = new Appearance((String) family.getSelectedItem(), (Integer) size.getValue(),
                spacing.getValue() / 100.0, margin.getValue() / 100.0, justify.isSelected(), hyphenate.isSelected());
        if (!a.equals(appearance)) {
            appearance = a;
            changed.accept(a);
        }
    }

    JComboBox<String> familyBox() {
        return family;
    }

    JSpinner sizeSpinner() {
        return size;
    }

    JCheckBox justifyBox() {
        return justify;
    }

    JToggleButton themeButton(ReadTheme theme) {
        return themes.get(theme);
    }
}
