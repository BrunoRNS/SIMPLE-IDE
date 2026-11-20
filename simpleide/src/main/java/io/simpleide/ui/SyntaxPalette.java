package io.simpleide.ui;

import java.awt.Color;

/**
 * Official syntax highlighting colors for one application theme.
 *
 * <p>
 * The values mirror the original editor themes (VS Code Dark+/Light+, Dracula, Alucard
 * and Gruvbox) and are applied verbatim. No WCAG contrast adjustment is performed, so the
 * original theme identity is preserved.
 *
 * @param comment comment text
 * @param commentKeyword keyword-like comment markup
 * @param keyword primary keyword
 * @param keywordSecondary secondary keyword
 * @param type data type
 * @param function function or method name
 * @param string string literal
 * @param number numeric literal
 * @param booleanLiteral boolean literal
 * @param punctuation punctuation and generic separators
 * @param operator operator token
 * @param separator separator token
 * @param identifier identifier
 * @param variable variable
 * @param annotation annotation
 * @param regex regular expression
 * @param error invalid token
 * @param markupTag markup tag
 * @param markupAttribute markup attribute
 * @param markupValue markup attribute value
 * @param markupOther other markup token
 * @param preprocessor preprocessor directive
 * @param hyperlink hyperlink
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public record SyntaxPalette(Color comment, Color commentKeyword, Color keyword, Color keywordSecondary, Color type,
		Color function, Color string, Color number, Color booleanLiteral, Color punctuation, Color operator,
		Color separator, Color identifier, Color variable, Color annotation, Color regex, Color error, Color markupTag,
		Color markupAttribute, Color markupValue, Color markupOther, Color preprocessor, Color hyperlink) {
}
