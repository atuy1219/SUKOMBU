package com.atuy.scomb.ui.viewmodel

import com.atuy.scomb.data.db.NewsItem
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class AdvancedNewsSearchInput(
    val allWords: String = "",
    val exactPhrase: String = "",
    val anyWords: String = "",
    val excludedWords: String = "",
    val titlePhrase: String = "",
    val authorPhrase: String = "",
    val since: String = "",
    val until: String = ""
) {
    fun toQuery(): String {
        val clauses = buildList {
            addAll(allWords.toTerms())
            exactPhrase.trim().takeIf(String::isNotEmpty)?.let { add(it.asQuotedPhrase()) }

            anyWords.toTerms()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" OR ")
                ?.let { add("($it)") }

            addAll(excludedWords.toTerms().map { "-$it" })

            titlePhrase.trim().takeIf(String::isNotEmpty)?.let {
                add("title:(${it.asQuotedPhrase()})")
            }
            authorPhrase.trim().takeIf(String::isNotEmpty)?.let {
                add("author:(${it.asQuotedPhrase()})")
            }
            since.trim().takeIf(String::isNotEmpty)?.let { add("since:$it") }
            until.trim().takeIf(String::isNotEmpty)?.let { add("until:$it") }
        }
        return clauses.joinToString(" ")
    }
}

private fun String.toTerms(): List<String> =
    trim().split(Regex("\\s+")).filter(String::isNotEmpty).map(String::asSearchTerm)

private fun String.asSearchTerm(): String =
    if (any { it.isWhitespace() || it in "()\"" } || this == "OR") {
        asQuotedPhrase()
    } else {
        this
    }

private fun String.asQuotedPhrase(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

class NewsSearchMatcher private constructor(
    private val expression: SearchExpression?,
    val error: String?
) {
    fun matches(item: NewsItem): Boolean = expression?.matches(item, SearchField.ANY) ?: true

    companion object {
        fun parse(query: String): NewsSearchMatcher {
            if (query.isBlank()) return NewsSearchMatcher(expression = null, error = null)

            return try {
                val parser = Parser(Lexer(query).tokenize())
                NewsSearchMatcher(expression = parser.parse(), error = null)
            } catch (e: SearchSyntaxException) {
                NewsSearchMatcher(expression = null, error = e.message)
            }
        }
    }
}

private enum class SearchField {
    ANY,
    TITLE,
    AUTHOR
}

private sealed interface SearchExpression {
    fun matches(item: NewsItem, field: SearchField): Boolean
}

private data class TermExpression(val value: String) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean {
        return when (field) {
            SearchField.ANY -> item.title.contains(value, ignoreCase = true) ||
                item.domain.contains(value, ignoreCase = true)
            SearchField.TITLE -> item.title.contains(value, ignoreCase = true)
            SearchField.AUTHOR -> item.domain.contains(value, ignoreCase = true)
        }
    }
}

private data class AndExpression(val expressions: List<SearchExpression>) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        expressions.all { it.matches(item, field) }
}

private data class OrExpression(val expressions: List<SearchExpression>) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        expressions.any { it.matches(item, field) }
}

private data class NotExpression(val expression: SearchExpression) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        !expression.matches(item, field)
}

private data class FieldExpression(
    val field: SearchField,
    val expression: SearchExpression
) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        expression.matches(item, this.field)
}

private data class SinceExpression(val date: LocalDate) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        item.publishDate()?.let { !it.isBefore(date) } ?: false
}

private data class UntilExpression(val date: LocalDate) : SearchExpression {
    override fun matches(item: NewsItem, field: SearchField): Boolean =
        item.publishDate()?.let { !it.isAfter(date) } ?: false
}

private fun NewsItem.publishDate(): LocalDate? {
    val value = publishTime.trim()
    if (value.length < 10) return null
    return runCatching { LocalDate.parse(value.substring(0, 10)) }.getOrNull()
}

private sealed interface SearchToken {
    data class Word(val value: String) : SearchToken
    data class Phrase(val value: String) : SearchToken
    data class Since(val value: String) : SearchToken
    data class Until(val value: String) : SearchToken
    data object FieldTitle : SearchToken
    data object FieldAuthor : SearchToken
    data object Or : SearchToken
    data object Not : SearchToken
    data object LeftParen : SearchToken
    data object RightParen : SearchToken
    data object End : SearchToken
}

private class Lexer(private val source: String) {
    private var index = 0

    fun tokenize(): List<SearchToken> {
        val tokens = mutableListOf<SearchToken>()
        while (index < source.length) {
            when {
                source[index].isWhitespace() -> index++
                source[index] == '(' -> {
                    tokens += SearchToken.LeftParen
                    index++
                }
                source[index] == ')' -> {
                    tokens += SearchToken.RightParen
                    index++
                }
                source[index] == '-' -> {
                    tokens += SearchToken.Not
                    index++
                }
                source[index] == '"' -> tokens += readPhrase()
                else -> tokens += readWord()
            }
        }
        tokens += SearchToken.End
        return tokens
    }

    private fun readPhrase(): SearchToken.Phrase {
        index++
        val value = StringBuilder()
        var escaped = false
        while (index < source.length) {
            val char = source[index++]
            when {
                escaped -> {
                    value.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '"' -> return SearchToken.Phrase(value.toString())
                else -> value.append(char)
            }
        }
        throw SearchSyntaxException("引用符が閉じられていません")
    }

    private fun readWord(): SearchToken {
        val start = index
        while (
            index < source.length &&
            !source[index].isWhitespace() &&
            source[index] !in "()\""
        ) {
            index++
        }
        val word = source.substring(start, index)
        if (word.isEmpty()) throw SearchSyntaxException("検索式を解析できません")

        return when {
            word == "OR" -> SearchToken.Or
            word.equals("title:", ignoreCase = true) -> SearchToken.FieldTitle
            word.equals("author:", ignoreCase = true) -> SearchToken.FieldAuthor
            word.startsWith("title:", ignoreCase = true) ->
                throw SearchSyntaxException("title: の検索範囲は title:(...) の形式で指定してください")
            word.startsWith("author:", ignoreCase = true) ->
                throw SearchSyntaxException("author: の検索範囲は author:(...) の形式で指定してください")
            word.startsWith("since:", ignoreCase = true) ->
                SearchToken.Since(word.substringAfter(':'))
            word.startsWith("until:", ignoreCase = true) ->
                SearchToken.Until(word.substringAfter(':'))
            else -> SearchToken.Word(word)
        }
    }
}

private class Parser(private val tokens: List<SearchToken>) {
    private var index = 0

    fun parse(): SearchExpression {
        val expression = parseOr()
        if (peek() != SearchToken.End) {
            throw SearchSyntaxException("検索式の末尾を解析できません")
        }
        return expression
    }

    private fun parseOr(): SearchExpression {
        val expressions = mutableListOf(parseAnd())
        while (peek() == SearchToken.Or) {
            consume()
            if (!startsExpression(peek())) {
                throw SearchSyntaxException("OR の後に検索条件が必要です")
            }
            expressions += parseAnd()
        }
        return expressions.singleOrNull() ?: OrExpression(expressions)
    }

    private fun parseAnd(): SearchExpression {
        val expressions = mutableListOf<SearchExpression>()
        while (startsExpression(peek())) {
            expressions += parseUnary()
        }
        if (expressions.isEmpty()) {
            throw SearchSyntaxException("検索条件が必要です")
        }
        return expressions.singleOrNull() ?: AndExpression(expressions)
    }

    private fun parseUnary(): SearchExpression {
        return when (val token = consume()) {
            SearchToken.Not -> {
                if (!startsExpression(peek())) {
                    throw SearchSyntaxException("- の後に除外条件が必要です")
                }
                NotExpression(parseUnary())
            }
            SearchToken.LeftParen -> {
                val expression = parseOr()
                requireToken<SearchToken.RightParen>("括弧が閉じられていません")
                expression
            }
            SearchToken.FieldTitle -> parseField(SearchField.TITLE, "title")
            SearchToken.FieldAuthor -> parseField(SearchField.AUTHOR, "author")
            is SearchToken.Since -> SinceExpression(parseDate(token.value, "since"))
            is SearchToken.Until -> UntilExpression(parseDate(token.value, "until"))
            is SearchToken.Word -> TermExpression(token.value)
            is SearchToken.Phrase -> TermExpression(token.value)
            else -> throw SearchSyntaxException("検索条件を解析できません")
        }
    }

    private fun parseField(field: SearchField, name: String): SearchExpression {
        if (consume() != SearchToken.LeftParen) {
            throw SearchSyntaxException("$name: の検索範囲は $name:(...) の形式で指定してください")
        }
        val expression = parseOr()
        requireToken<SearchToken.RightParen>("$name:(...) の括弧が閉じられていません")
        return FieldExpression(field, expression)
    }

    private fun parseDate(value: String, operator: String): LocalDate {
        if (value.isBlank()) {
            throw SearchSyntaxException("$operator: の後に YYYY-MM-DD 形式の日付が必要です")
        }
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw SearchSyntaxException("$operator:$value は有効な YYYY-MM-DD 形式ではありません")
        }
    }

    private inline fun <reified T : SearchToken> requireToken(message: String) {
        if (consume() !is T) throw SearchSyntaxException(message)
    }

    private fun startsExpression(token: SearchToken): Boolean = when (token) {
        SearchToken.Not,
        SearchToken.LeftParen,
        SearchToken.FieldTitle,
        SearchToken.FieldAuthor,
        is SearchToken.Since,
        is SearchToken.Until,
        is SearchToken.Word,
        is SearchToken.Phrase -> true
        SearchToken.Or,
        SearchToken.RightParen,
        SearchToken.End -> false
    }

    private fun peek(): SearchToken = tokens[index]

    private fun consume(): SearchToken = tokens[index++]
}

private class SearchSyntaxException(message: String) : IllegalArgumentException(message)
