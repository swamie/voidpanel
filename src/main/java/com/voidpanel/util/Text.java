package com.voidpanel.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Legacy "&" colour-code parser plus a tiny template engine.
 * Supports &0-&f, &k-&o, &r and &#RRGGBB hex colours.
 */
public final class Text {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
	private static final Pattern CODES = Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])");

	private Text() {}

	/** Parse a string containing & codes into a component. */
	public static MutableComponent of(String legacy) {
		MutableComponent out = Component.empty();
		parseInto(out, legacy, Style.EMPTY);
		return out;
	}

	/**
	 * Fill a template like "&7{display}&8: &f{message}". Placeholder values are inserted
	 * as components (never re-parsed), inheriting whatever colour was active before them.
	 */
	public static MutableComponent format(String template, Map<String, Component> values) {
		MutableComponent out = Component.empty();
		Style style = Style.EMPTY;
		Matcher m = PLACEHOLDER.matcher(template);
		int last = 0;
		while (m.find()) {
			style = parseInto(out, template.substring(last, m.start()), style);
			Component value = values.get(m.group(1));
			if (value != null) {
				out.append(Component.empty().withStyle(style).append(value));
			} else {
				style = parseInto(out, m.group(), style);
			}
			last = m.end();
		}
		parseInto(out, template.substring(last), style);
		return out;
	}

	/** The style left active at the end of a legacy string, e.g. "&b&l" -> aqua bold. */
	public static Style styleOf(String legacy) {
		return legacy == null ? Style.EMPTY : parseInto(Component.empty(), legacy, Style.EMPTY);
	}

	/** Remove & codes, leaving only visible characters. */
	public static String strip(String legacy) {
		return CODES.matcher(legacy).replaceAll("");
	}

	private static Style parseInto(MutableComponent out, String text, Style start) {
		Style style = start;
		StringBuilder buf = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (code == '#' && i + 8 <= text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
					flush(out, buf, style);
					style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
					i += 8;
					continue;
				}
				ChatFormatting fmt = ChatFormatting.getByCode(code);
				if (fmt != null) {
					flush(out, buf, style);
					if (fmt == ChatFormatting.RESET) {
						style = Style.EMPTY;
					} else if (fmt.ordinal() <= ChatFormatting.WHITE.ordinal()) {
						// Like vanilla legacy codes, a colour clears bold/italic/etc.
						style = Style.EMPTY.withColor(fmt);
					} else {
						style = style.applyFormat(fmt);
					}
					i += 2;
					continue;
				}
			}
			buf.append(c);
			i++;
		}
		flush(out, buf, style);
		return style;
	}

	private static void flush(MutableComponent out, StringBuilder buf, Style style) {
		if (buf.isEmpty()) return;
		out.append(Component.literal(buf.toString()).withStyle(style));
		buf.setLength(0);
	}
}
