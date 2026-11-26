package net.serlith.sakurabans.api.punish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.junit.jupiter.api.Test;

class PunishmentQueryTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void theDefaultSelectsEverything() {
        final PunishmentQuery query = PunishmentQuery.builder().build();

        assertThat(query.types()).containsExactlyInAnyOrder(PunishmentType.values());
        assertThat(query.activeOnly()).isFalse();
        assertThat(query.includeHidden()).isTrue();
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(10);
        assertThat(query.toBuilder().build()).isEqualTo(query);
    }

    @Test
    void victimAndApplicabilityExcludeEachOther() {
        final UUID uuid = UUID.randomUUID();

        final PunishmentQuery.Applicability connection =
                PunishmentQuery.Applicability.of(uuid, null, AddressStrictness.NORMAL);

        assertThatThrownBy(() -> PunishmentQuery.builder()
                .victim(PlayerVictim.of(uuid))
                .applicableTo(connection)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsAndPagesAreChecked() {
        assertThatThrownBy(() -> PunishmentQuery.builder().types(Set.of()).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PunishmentQuery.builder().createdAfter(T2).createdBefore(T1)
                .build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PunishmentQuery.builder().page(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PunishmentQuery.builder()
                .pageSize(PunishmentQuery.MAX_PAGE_SIZE + 1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PunishmentQuery.builder().types(PunishmentType.BAN, PunishmentType.MUTE)
                .createdAfter(T1).createdBefore(T2).build().types()).hasSize(2);
    }

    @Test
    void newFiltersHaveSafeDefaults() {
        final PunishmentQuery query = PunishmentQuery.builder().build();

        assertThat(query.includeVoided()).isFalse();
        assertThat(query.order()).isEqualTo(PunishmentQuery.Order.NEWEST_FIRST);
        assertThat(query.after()).isEmpty();
    }

    @Test
    void validateReportsEveryProblemWithPath() {
        final List<FieldError> errors = PunishmentQuery.builder()
                .types(Set.of())
                .createdAfter(T2).createdBefore(T1)
                .page(2).after("cursor")
                .pageSize(0)
                .validate();

        assertThat(errors).extracting(FieldError::path, FieldError::code).containsExactlyInAnyOrder(
                tuple("types", Code.OUT_OF_RANGE),
                tuple("createdAfter", Code.WRONG_ORDER),
                tuple("pageSize", Code.OUT_OF_RANGE),
                tuple("after", Code.EXCLUSIVE));
        assertThatThrownBy(() -> PunishmentQuery.builder().pageSize(0).build())
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).hasSize(1));
    }

    @Test
    void applicabilityNeedsAnAccountOrAnAddress() throws Exception {
        final InetAddress address = InetAddress.getByName("1.2.3.4");

        assertThat(PunishmentQuery.Applicability.of(null, address, AddressStrictness.NORMAL)
                .uuid()).isEmpty();
        assertThatThrownBy(() -> PunishmentQuery.Applicability.of(
                null, null, AddressStrictness.NORMAL)).isInstanceOf(ValidationException.class);
    }

    @Test
    void pagesCountFromTheTotal() {
        assertThat(page(0, 1, 10).pageCount()).isEqualTo(1);
        assertThat(page(10, 1, 10).pageCount()).isEqualTo(1);
        assertThat(page(11, 1, 10).pageCount()).isEqualTo(2);
        assertThat(page(11, 1, 10).hasNext()).isTrue();
        assertThat(page(11, 2, 10).hasNext()).isFalse();
    }

    private static Page<String> page(final long total, final int page, final int pageSize) {
        return new Page<>() {
            @Override
            public List<String> items() {
                return List.of();
            }

            @Override
            public long total() {
                return total;
            }

            @Override
            public int page() {
                return page;
            }

            @Override
            public int pageSize() {
                return pageSize;
            }

            @Override
            public Optional<String> nextCursor() {
                return Optional.empty();
            }
        };
    }
}
