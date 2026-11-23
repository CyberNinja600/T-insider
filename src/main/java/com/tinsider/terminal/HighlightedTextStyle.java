package com.tinsider.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;

import java.util.EnumSet;

/**
 * A TextStyle that represents an applied word or regex highlight in the terminal.
 * It tracks the highlight ID that applied it and remembers the character's original
 * TextStyle (e.g. native ANSI colors from grep, ls, compiler errors, or default style)
 * so it can be restored perfectly when the highlight is deactivated.
 */
public class HighlightedTextStyle extends TextStyle {
    private final String highlightId;
    private final TextStyle originalStyle;

    public record RestoreSpan(int start, String text, TextStyle originalStyle) {}

    public HighlightedTextStyle(
            TerminalColor foreground,
            TerminalColor background,
            String highlightId,
            TextStyle originalStyle
    ) {
        super(foreground, background, extractOptions(originalStyle));
        this.highlightId = highlightId;
        if (originalStyle instanceof HighlightedTextStyle hts) {
            this.originalStyle = hts.getOriginalStyle();
        } else {
            this.originalStyle = (originalStyle != null) ? originalStyle : TextStyle.EMPTY;
        }
    }

    public String getHighlightId() {
        return highlightId;
    }

    public TextStyle getOriginalStyle() {
        return originalStyle;
    }

    private static EnumSet<Option> extractOptions(TextStyle style) {
        if (style == null) {
            return EnumSet.noneOf(Option.class);
        }
        EnumSet<Option> options = EnumSet.noneOf(Option.class);
        for (Option opt : Option.values()) {
            if (style.hasOption(opt)) {
                options.add(opt);
            }
        }
        return options;
    }
}
