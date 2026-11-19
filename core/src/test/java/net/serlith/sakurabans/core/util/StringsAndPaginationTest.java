package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StringsAndPaginationTest {

    @Test
    void lowerUsesTheRootLocale() {
        assertThat(Strings.lower("TITLE")).isEqualTo("title");
    }

    @Test
    void truncateKeepsSurrogatePairsWhole() {
        assertThat(Strings.truncate("hello", 10)).isEqualTo("hello");
        assertThat(Strings.truncate("hello", 3)).isEqualTo("hel");
        assertThat(Strings.truncate("ab😀", 3)).isEqualTo("ab");
        assertThat(Strings.truncate("abc", 0)).isEmpty();
        assertThatThrownBy(() -> Strings.truncate("abc", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Notch", "a", "jeb_", "ABCDEFGHIJKLMNOP"})
    void acceptsJavaNames(final String name) {
        assertThat(Strings.isValidPlayerName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCDEFGHIJKLMNOPQ", "with space", "dash-name", "ünicode"})
    void rejectsOtherNames(final String name) {
        assertThat(Strings.isValidPlayerName(name)).isFalse();
    }

    @Test
    void acceptsFloodgateNamesWithTheirPrefix() {
        assertThat(Strings.isValidPlayerName(".Bedrock_Player", ".")).isTrue();
        assertThat(Strings.isValidPlayerName("." + "a".repeat(31), ".")).isTrue();
        assertThat(Strings.isValidPlayerName("." + "a".repeat(32), ".")).isFalse();
        assertThat(Strings.isValidPlayerName(".Bedrock Player", ".")).isFalse();
        assertThat(Strings.isValidPlayerName(".Bedrock_Player", "")).isFalse();
    }

    @Test
    void paginationHandlesNoneOneAndManyPages() {
        assertThat(Pagination.pageCount(0, 10)).isEqualTo(1);
        assertThat(Pagination.pageCount(10, 10)).isEqualTo(1);
        assertThat(Pagination.pageCount(11, 10)).isEqualTo(2);
        assertThat(Pagination.pageCount(1_000_001, 10)).isEqualTo(100_001);
        assertThat(Pagination.clamp(0, 5)).isEqualTo(1);
        assertThat(Pagination.clamp(9, 5)).isEqualTo(5);
        assertThat(Pagination.clamp(3, 0)).isEqualTo(1);
        assertThat(Pagination.offset(1, 10)).isZero();
        assertThat(Pagination.offset(3, 10)).isEqualTo(20);
        assertThatThrownBy(() -> Pagination.pageCount(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
