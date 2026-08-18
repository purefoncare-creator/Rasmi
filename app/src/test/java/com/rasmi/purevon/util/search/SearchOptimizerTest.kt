package com.rasmi.purevon.util.search

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SearchOptimizerTest {

    @Before
    fun setUp() {
        SearchOptimizer.clearCache()
        SearchOptimizer.clearHistory()
    }

    // highlightMatches
    @Test
    fun `highlightMatches wraps query in tags`() {
        val result = SearchOptimizer.highlightMatches("Hello World", "World")
        assertThat(result).contains("<b>World</b>")
    }

    @Test
    fun `highlightMatches case insensitive`() {
        val result = SearchOptimizer.highlightMatches("Hello World", "hello")
        assertThat(result).contains("<b>Hello</b>")
    }

    @Test
    fun `highlightMatches multiple terms`() {
        val result = SearchOptimizer.highlightMatches("Hello World", "Hello World")
        assertThat(result).contains("<b>Hello</b>")
        assertThat(result).contains("<b>World</b>")
    }

    @Test
    fun `highlightMatches returns original for blank query`() {
        assertThat(SearchOptimizer.highlightMatches("Hello", "")).isEqualTo("Hello")
        assertThat(SearchOptimizer.highlightMatches("Hello", "   ")).isEqualTo("Hello")
    }

    // extractSnippet
    @Test
    fun `extractSnippet returns full text if short enough`() {
        val text = "Short text"
        assertThat(SearchOptimizer.extractSnippet(text, "Short", 100)).isEqualTo(text)
    }

    @Test
    fun `extractSnippet truncates long text with ellipsis`() {
        val text = "A".repeat(200)
        val snippet = SearchOptimizer.extractSnippet(text, "B", 50)
        assertThat(snippet.length).isAtMost(56) // 50 + "..." + possible prefix
    }

    @Test
    fun `extractSnippet centers around match`() {
        val text = "AAAA BBBB CCCC"
        val snippet = SearchOptimizer.extractSnippet(text, "BBBB", 10)
        assertThat(snippet).contains("BBBB")
    }

    // calculateRelevance
    @Test
    fun `calculateRelevance higher for exact match`() {
        val score = SearchOptimizer.calculateRelevance("Hello World", "Hello World")
        assertThat(score).isGreaterThan(0.0)
    }

    @Test
    fun `calculateRelevance zero for blank query`() {
        assertThat(SearchOptimizer.calculateRelevance("Hello", "")).isEqualTo(0.0)
    }

    @Test
    fun `calculateRelevance higher for prefix match`() {
        val score = SearchOptimizer.calculateRelevance("Hello there", "Hello")
        assertThat(score).isGreaterThan(5.0)
    }

    // History
    @Test
    fun `addToHistory adds query`() {
        SearchOptimizer.addToHistory("test query")
        assertThat(SearchOptimizer.getHistory()).contains("test query")
    }

    @Test
    fun `addToHistory ignores short queries`() {
        SearchOptimizer.addToHistory("a")
        assertThat(SearchOptimizer.getHistory()).isEmpty()
    }

    @Test
    fun `addToHistory does not duplicate`() {
        SearchOptimizer.addToHistory("unique")
        SearchOptimizer.addToHistory("unique")
        assertThat(SearchOptimizer.getHistory().filter { it == "unique" }).hasSize(1)
    }

    @Test
    fun `clearHistory removes all`() {
        SearchOptimizer.addToHistory("test")
        SearchOptimizer.clearHistory()
        assertThat(SearchOptimizer.getHistory()).isEmpty()
    }

    // Cache
    @Test
    fun `cacheResults and getCachedResults round-trip`() {
        SearchOptimizer.cacheResults("query", listOf(1, 2, 3))
        val cached = SearchOptimizer.getCachedResults<List<Int>>("query")
        assertThat(cached).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun `getCachedResults returns null for non-existent key`() {
        assertThat(SearchOptimizer.getCachedResults<String>("nope")).isNull()
    }

    @Test
    fun `clearCache removes all entries`() {
        SearchOptimizer.cacheResults("q1", "r1")
        SearchOptimizer.clearCache()
        assertThat(SearchOptimizer.getCachedResults<String>("q1")).isNull()
    }

    @Test
    fun `getCachedResults returns null for expired entry`() {
        SearchOptimizer.cacheResults("expired_q", "data", expiryMs = 1)
        Thread.sleep(10)
        assertThat(SearchOptimizer.getCachedResults<String>("expired_q")).isNull()
    }

    // Suggestions
    @Test
    fun `getSuggestions returns empty for short query`() {
        assertThat(SearchOptimizer.getSuggestions("a")).isEmpty()
    }

    @Test
    fun `getSuggestions returns matching history`() {
        SearchOptimizer.addToHistory("hello world")
        SearchOptimizer.addToHistory("hello there")
        val suggestions = SearchOptimizer.getSuggestions("hello")
        assertThat(suggestions).isNotEmpty()
    }
}

class SearchSuggestionGeneratorTest {

    @Test
    fun `generateSuggestions returns history for short query`() {
        val generator = SearchSuggestionGenerator()
        val suggestions = generator.generateSuggestions(
            "a", listOf("hello", "world"), emptyList(), 5
        )
        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.all { it.type == SuggestionType.HISTORY }).isTrue()
    }

    @Test
    fun `generateSuggestions returns mixed results for longer query`() {
        val generator = SearchSuggestionGenerator()
        val suggestions = generator.generateSuggestions(
            "hel", listOf("hello", "help", "world"), listOf("Helen"), 10
        )
        assertThat(suggestions).isNotEmpty()
    }

    @Test
    fun `generateSuggestions respects limit`() {
        val generator = SearchSuggestionGenerator()
        val history = (1..20).map { "query $it" }
        val suggestions = generator.generateSuggestions("q", history, limit = 5)
        assertThat(suggestions.size).isAtMost(5)
    }
}
