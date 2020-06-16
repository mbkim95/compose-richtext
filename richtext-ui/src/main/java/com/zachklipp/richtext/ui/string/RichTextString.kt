@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry", "SuspiciousCollectionReassignment")

package com.zachklipp.richtext.ui.string

import androidx.compose.Immutable
import androidx.ui.graphics.Color
import androidx.ui.text.AnnotatedString
import androidx.ui.text.SpanStyle
import androidx.ui.text.annotatedString
import androidx.ui.text.appendInlineContent
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontWeight
import androidx.ui.text.length
import androidx.ui.text.style.BaselineShift
import androidx.ui.text.style.TextDecoration
import androidx.ui.unit.sp
import com.zachklipp.richtext.ui.DefaultCodeBlockBackground
import com.zachklipp.richtext.ui.string.RichTextString.Builder
import com.zachklipp.richtext.ui.string.RichTextString.Format
import com.zachklipp.richtext.ui.string.RichTextString.Format.Bold
import com.zachklipp.richtext.ui.string.RichTextString.Format.Code
import com.zachklipp.richtext.ui.string.RichTextString.Format.Companion.FormatAnnotationScope
import com.zachklipp.richtext.ui.string.RichTextString.Format.Italic
import com.zachklipp.richtext.ui.string.RichTextString.Format.Link
import com.zachklipp.richtext.ui.string.RichTextString.Format.Strikethrough
import com.zachklipp.richtext.ui.string.RichTextString.Format.Subscript
import com.zachklipp.richtext.ui.string.RichTextString.Format.Superscript
import com.zachklipp.richtext.ui.string.RichTextString.Format.Underline
import java.util.UUID
import kotlin.LazyThreadSafetyMode.NONE

/** Copied from inline content. */
@PublishedApi
internal const val REPLACEMENT_CHAR = "\uFFFD"

@Immutable
data class RichTextStringStyle(
  val boldStyle: SpanStyle? = null,
  val italicStyle: SpanStyle? = null,
  val underlineStyle: SpanStyle? = null,
  val strikethroughStyle: SpanStyle? = null,
  val subscriptStyle: SpanStyle? = null,
  val superscriptStyle: SpanStyle? = null,
  val codeStyle: SpanStyle? = null,
  val linkStyle: SpanStyle? = null
) {
  internal fun merge(otherStyle: RichTextStringStyle?): RichTextStringStyle {
    if (otherStyle == null) return this
    return RichTextStringStyle(
      boldStyle = boldStyle.merge(otherStyle.boldStyle),
      italicStyle = italicStyle.merge(otherStyle.italicStyle),
      underlineStyle = underlineStyle.merge(otherStyle.underlineStyle),
      strikethroughStyle = strikethroughStyle.merge(otherStyle.strikethroughStyle),
      subscriptStyle = subscriptStyle.merge(otherStyle.subscriptStyle),
      superscriptStyle = superscriptStyle.merge(otherStyle.superscriptStyle),
      codeStyle = codeStyle.merge(otherStyle.codeStyle),
      linkStyle = linkStyle.merge(otherStyle.linkStyle)
    )
  }

  internal fun resolveDefaults(): RichTextStringStyle =
    RichTextStringStyle(
      boldStyle = boldStyle ?: Bold.DefaultStyle,
      italicStyle = italicStyle ?: Italic.DefaultStyle,
      underlineStyle = underlineStyle ?: Underline.DefaultStyle,
      strikethroughStyle = strikethroughStyle ?: Strikethrough.DefaultStyle,
      subscriptStyle = subscriptStyle ?: Subscript.DefaultStyle,
      superscriptStyle = superscriptStyle ?: Superscript.DefaultStyle,
      codeStyle = codeStyle ?: Code.DefaultStyle,
      linkStyle = linkStyle ?: Link.DefaultStyle
    )

  companion object {
    val Default = RichTextStringStyle()

    private fun SpanStyle?.merge(otherStyle: SpanStyle?): SpanStyle? =
      this?.merge(otherStyle) ?: otherStyle
  }
}

inline fun richTextString(builder: Builder.() -> Unit): RichTextString =
  Builder().apply(builder)
    .toRichTextString()

/**
 * TODO write documentation
 */
@Immutable
data class RichTextString internal constructor(
  private val taggedString: AnnotatedString,
  internal val formatObjects: Map<String, Any>
) {

  val length: Int get() = taggedString.length
  val text: String get() = taggedString.text

  operator fun plus(other: RichTextString): RichTextString =
    Builder(length + other.length).run {
      append(this@RichTextString)
      append(other)
      toRichTextString()
    }

  internal fun toAnnotatedString(style: RichTextStringStyle, contentColor: Color): AnnotatedString =
    annotatedString {
      append(taggedString)

      // Get all of our format annotations.
      val tags = taggedString.getStringAnnotations(FormatAnnotationScope, 0, taggedString.length)
      // And apply their actual SpanStyles to the string.
      tags.forEach { range ->
        val format = Format.findTag(range.item, formatObjects) ?: return@forEach
        format.getStyle(style, contentColor)
          ?.let { spanStyle -> addStyle(spanStyle, range.start, range.end) }
      }
    }

  internal fun getInlineContents(): Map<String, InlineContent> =
    formatObjects.asSequence()
      .mapNotNull { (tag, format) ->
        tag.removePrefix("inline:")
          // If no prefix was found then we ignore it.
          .takeUnless { it === tag }
          ?.let {
            @Suppress("UNCHECKED_CAST")
            Pair(it, format as InlineContent)
          }
      }
      .toMap()

  sealed class Format(private val simpleTag: String? = null) {

    internal open fun getStyle(
      richTextStyle: RichTextStringStyle,
      contentColor: Color
    ): SpanStyle? = null

    object Italic : Format("italic") {
      internal val DefaultStyle = SpanStyle(fontStyle = FontStyle.Italic)
      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.italicStyle
    }

    object Bold : Format(simpleTag = "foo") {
      internal val DefaultStyle = SpanStyle(fontWeight = FontWeight.Bold)
      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.boldStyle
    }

    object Underline : Format("underline") {
      internal val DefaultStyle = SpanStyle(textDecoration = TextDecoration.Underline)
      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.underlineStyle
    }

    object Strikethrough : Format("strikethrough") {
      internal val DefaultStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.strikethroughStyle
    }

    object Subscript : Format("subscript") {
      internal val DefaultStyle = SpanStyle(
        baselineShift = BaselineShift(-0.2f),
        // TODO this should be relative to current font size
        fontSize = 10.sp
      )

      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.subscriptStyle
    }

    object Superscript : Format("superscript") {
      internal val DefaultStyle = SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = 10.sp
      )

      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.superscriptStyle
    }

    object Code : Format("code") {
      internal val DefaultStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        background = DefaultCodeBlockBackground
      )

      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.codeStyle
    }

    data class Link(val onClick: () -> Unit) : Format() {
      override fun getStyle(
        richTextStyle: RichTextStringStyle,
        contentColor: Color
      ) = richTextStyle.linkStyle!!.let { style ->
        // Tweak the colors a bit to make it more likely to contrast with the background color.
        val averagedValues = Color(
          red = ((contentColor.red + style.color.red) * .5f
              + style.color.red * .5f).coerceAtMost(1f),
          green = ((contentColor.green + style.color.green) * .5f
              + style.color.green * .5f).coerceAtMost(1f),
          blue = ((contentColor.blue + style.color.blue) * .5f
              + style.color.blue * .5f).coerceAtMost(1f)
        )
        style.copy(color = averagedValues)
      }

      internal companion object {
        val DefaultStyle = SpanStyle(
          textDecoration = TextDecoration.Underline,
          color = Color.Blue
        )
      }
    }

    internal fun registerTag(tags: MutableMap<String, Any>): String {
      simpleTag?.let { return it }
      val uuid = UUID.randomUUID().toString()
      tags[uuid] = this
      return "format:$uuid"
    }

    internal companion object {
      val FormatAnnotationScope = Format::class.java.name

      // For some reason, if this isn't lazy, Bold will always be null. Is Compose messing up static
      // initialization order?
      private val simpleTags by lazy(NONE) {
        listOf(Bold, Italic, Underline, Strikethrough, Subscript, Superscript, Code)
      }

      fun findTag(
        tag: String,
        tags: Map<String, Any>
      ): Format? {
        val stripped = tag.removePrefix("format:")
        return if (stripped === tag) {
          // If the original string was returned, it means the string did not have the prefix.
          simpleTags.firstOrNull { it.simpleTag == tag }
        } else {
          tags[stripped] as? Format
        }
      }
    }
  }

  class Builder(capacity: Int = 16) {
    private val builder = AnnotatedString.Builder(capacity)
    private val formatObjects = mutableMapOf<String, Any>()

    fun addFormat(
      format: Format,
      start: Int,
      end: Int
    ) {
      val tag = format.registerTag(formatObjects)
      builder.addStringAnnotation(FormatAnnotationScope, tag, start, end)
    }

    fun pushFormat(format: Format): Int {
      val tag = format.registerTag(formatObjects)
      return builder.pushStringAnnotation(FormatAnnotationScope, tag)
    }

    fun pop() = builder.pop()

    fun pop(index: Int) = builder.pop(index)

    fun append(text: String) = builder.append(text)

    fun append(text: RichTextString) {
      builder.append(text.taggedString)
      formatObjects.putAll(text.formatObjects)
    }

    fun appendInlineContent(alternateText: String = REPLACEMENT_CHAR, content: InlineContent) {
      val tag = UUID.randomUUID().toString()
      formatObjects["inline:$tag"] = content
      builder.appendInlineContent(tag, alternateText)
    }

    /**
     * Provides access to the underlying builder, which can be used to add arbitrary formatting,
     * including mixed with formatting from this Builder.
     */
    fun <T> withAnnotatedString(block: AnnotatedString.Builder.() -> T): T = builder.block()

    fun toRichTextString(): RichTextString =
      RichTextString(
        builder.toAnnotatedString(),
        formatObjects.toMap()
      )
  }
}

inline fun Builder.withFormat(
  format: Format,
  block: Builder.() -> Unit
) {
  val index = pushFormat(format)
  block()
  pop(index)
}
